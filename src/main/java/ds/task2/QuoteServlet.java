/**
 * Author: Luhan Huang
 * AndrewID: luhanh
 * Course: 95-702 Distributed Systems
 * Project 4
 *
 * This file is part of my submission for Project .
 */
package ds.task2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet-only implementation of the crypto quote API.
 *
 * GET /api/health
 *   -> { "status": "ok", "time": "..." }
 *
 * GET /api/quote?coin=bitcoin&vs=usd
 *   -> { coin, vs, price, change24h_pct, volatility, asOf }
 *
 * Also logs each /api/quote request to MongoDB Atlas (database: cryptoapp, collection: logs).
 * Logging is NOT done for the dashboard.
 */
public class QuoteServlet extends HttpServlet {

    /** Simple in-memory cache entry. */
    private static class CacheEntry {
        final Map<String, Object> payload;
        final long expiresAtMs;
        CacheEntry(Map<String, Object> payload, long expiresAtMs) {
            this.payload = payload;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private MongoCollection<Document> logs;
    private long cacheTtlMs = 15_000; // 15 seconds

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // Read cache TTL from init-param if present
        String ttl = config.getInitParameter("CACHE_TTL_MS");
        if (ttl != null && !ttl.isBlank()) {
            try {
                cacheTtlMs = Long.parseLong(ttl.trim());
            } catch (NumberFormatException ignored) {}
        }

        // Mongo URI: get env name from init-param, default ATLAS_URI
        String envVarName = Optional.ofNullable(config.getInitParameter("ATLAS_URI_ENV"))
                .orElse("ATLAS_URI");

        String uri = System.getenv(envVarName);
        if (uri == null || uri.isBlank()) {
            throw new ServletException("Missing MongoDB Atlas URI in env var: " + envVarName);
        }

        try {
            MongoClient client = MongoClients.create(uri);
            MongoDatabase db = client.getDatabase("cryptoapp");
            logs = db.getCollection("logs");
        } catch (Exception e) {
            throw new ServletException("Failed to initialize Mongo connection: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();

        // ---- /api/health (no logging) ----
        if (path.endsWith("/health")) {
            writeJson(resp, 200, Map.of("status", "ok", "time", Instant.now().toString()));
            return;
        }

        // ---- /api/quote ----
        String coin = opt(req.getParameter("coin")).toLowerCase(Locale.ROOT);
        String vs   = opt(req.getParameter("vs")).toLowerCase(Locale.ROOT);

        if (coin.isBlank() || vs.isBlank()) {
            writeJson(resp, 400, Map.of("error", "coin and vs are required"));
            return;
        }

        long startNano = System.nanoTime();
        String cacheKey = coin + "|" + vs;

        // Cache first
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
            writeJson(resp, 200, cached.payload);
            log(req, coin, vs, 0L, 200, cached.payload, true, null, startNano);
            return;
        }

        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" +
                URLEncoder.encode(coin, StandardCharsets.UTF_8) +
                "&vs_currencies=" + URLEncoder.encode(vs, StandardCharsets.UTF_8) +
                "&include_24hr_change=true";

        int upstreamStatus = -1;
        long upstreamStart = System.nanoTime();
        Map<String, Object> resultBody = null;
        String error = null;

        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            upstreamStatus = response.statusCode();

            if (upstreamStatus == 200) {
                JsonNode root = mapper.readTree(response.body());
                if (root.has(coin) && root.get(coin).has(vs)) {
                    double price = root.get(coin).get(vs).asDouble();
                    JsonNode chNode = root.get(coin).get(vs + "_24h_change");
                    double change = (chNode != null && !chNode.isNull()) ? chNode.asDouble() : 0.0;

                    String volatility = classifyVolatility(change);

                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("coin", coin);
                    out.put("vs", vs);
                    out.put("price", price);
                    out.put("change24h_pct", change);
                    out.put("volatility", volatility);
                    out.put("asOf", Instant.now().toString());

                    // update cache
                    cache.put(cacheKey, new CacheEntry(out, System.currentTimeMillis() + cacheTtlMs));

                    resultBody = out;
                    writeJson(resp, 200, out);
                    log(req, coin, vs, ms(upstreamStart), 200, out, false, null, startNano);
                    return;
                } else {
                    error = "invalid upstream data";
                }
            } else {
                error = "upstream status " + upstreamStatus;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        } catch (Exception e) {
            error = "exception: " + e.getClass().getSimpleName();
        }

        // Error path
        Map<String, Object> err = Map.of("error", error);
        writeJson(resp, 502, err);
        log(req, coin, vs, ms(upstreamStart), upstreamStatus, null, false, error, startNano);
    }

    private void writeJson(HttpServletResponse resp, int status, Map<String, Object> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        try (PrintWriter out = resp.getWriter()) {
            out.write(mapper.writeValueAsString(body));
        }
    }

    private String classifyVolatility(double pct) {
        double abs = Math.abs(pct);
        if (abs < 2.0) return "calm";
        if (abs < 5.0) return "moderate";
        return "volatile";
    }

    private void log(HttpServletRequest req,
                     String coin,
                     String vs,
                     long upstreamLatencyMs,
                     int upstreamStatus,
                     Map<String, Object> result,
                     boolean cacheHit,
                     String error,
                     long startNano) {
        try {
            Document doc = new Document("ts", Instant.now().toString())
                    .append("device", new Document("model", header(req, "X-Device-Model"))
                            .append("sdk", header(req, "X-Device-SDK")))
                    .append("clientIp", req.getRemoteAddr())
                    .append("req", new Document("coin", coin).append("vs", vs))
                    .append("upstream", new Document("provider", "coingecko")
                            .append("endpoint", "/simple/price")
                            .append("latencyMs", upstreamLatencyMs)
                            .append("status", upstreamStatus))
                    .append("result", result == null ? null : new Document(result))
                    .append("serverLatencyMs", ms(startNano))
                    .append("status", error == null ? 200 : 502)
                    .append("error", error)
                    .append("cacheHit", cacheHit)
                    .append("appVersion", "1.0.0");

            logs.insertOne(doc);
        } catch (Exception ignored) {
            // logging must never break the service
        }
    }

    private static String header(HttpServletRequest req, String name) {
        return Optional.ofNullable(req.getHeader(name)).orElse("");
    }

    private static long ms(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    private static String opt(String s) {
        return s == null ? "" : s;
    }
}
