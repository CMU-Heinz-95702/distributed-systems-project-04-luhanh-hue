package ds.task2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GET /api/quote?coin=bitcoin&vs=usd
 * - Validates input
 * - Calls CoinGecko simple/price
 * - Derives a 'volatility' label from 24h % change
 * - Returns trimmed JSON {coin, vs, price, change24h_pct, volatility, asOf}
 * - Logs the interaction into MongoDB Atlas (collection: cryptoapp.logs)
 *
 * GET /api/health
 * - Returns {"status":"ok","time":"..."}
 *
 * Mongo:
 *   env var ATLAS_URI should be set in Codespaces; fallback to init-param is supported.
 */
public class QuoteServlet extends HttpServlet {

    // Simple in-memory cache to reduce upstream calls / rate impact
    private static class CacheEntry {
        final Map<String, Object> payload;
        final long expiresAt;
        CacheEntry(Map<String,Object> payload, long expiresAt){ this.payload=payload; this.expiresAt=expiresAt; }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private MongoCollection<Document> logs;
    private long cacheTtlMs = 15000; // default

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // Read cache TTL
        String ttl = config.getInitParameter("CACHE_TTL_MS");
        if (ttl != null && !ttl.isBlank()) {
            try { cacheTtlMs = Long.parseLong(ttl.trim()); } catch (NumberFormatException ignored) {}
        }

        // Mongo URI: prefer environment variable, else fail
        String envVarName = Optional.ofNullable(config.getInitParameter("ATLAS_URI_ENV")).orElse("ATLAS_URI");
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
        if (path.endsWith("/health")) {
            writeJson(resp, 200, Map.of("status", "ok", "time", Instant.now().toString()));
            return;
        }

        // -------- /api/quote --------
        String coin = opt(req.getParameter("coin")).toLowerCase(Locale.ROOT);
        String vs   = opt(req.getParameter("vs")).toLowerCase(Locale.ROOT);
        if (coin.isBlank() || vs.isBlank()) {
            writeJson(resp, 400, Map.of("error", "coin and vs are required"));
            return;
        }
        long start = System.nanoTime();

        // Try cache
        String cacheKey = coin + "|" + vs;
        CacheEntry ce = cache.get(cacheKey);
        if (ce != null && System.currentTimeMillis() < ce.expiresAt) {
            writeJson(resp, 200, ce.payload);
            log(req, coin, vs, 0, 200, ce.payload, true, null, start);
            return;
        }

        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" +
                URLEncoder.encode(coin, StandardCharsets.UTF_8) +
                "&vs_currencies=" + URLEncoder.encode(vs, StandardCharsets.UTF_8) +
                "&include_24hr_change=true";

        int upstreamStatus = -1;
        long upstreamStart = System.nanoTime();
        Map<String, Object> out;
        String err = null;

        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            upstreamStatus = r.statusCode();

            if (upstreamStatus == 200) {
                JsonNode root = mapper.readTree(r.body());
                if (root.has(coin) && root.get(coin).has(vs)) {
                    double price = root.get(coin).get(vs).asDouble();
                    JsonNode chNode = root.get(coin).get(vs + "_24h_change");
                    double change = (chNode != null && !chNode.isNull()) ? chNode.asDouble() : 0.0;
                    String volatility = volatility(change);

                    out = new LinkedHashMap<>();
                    out.put("coin", coin);
                    out.put("vs", vs);
                    out.put("price", price);
                    out.put("change24h_pct", change);
                    out.put("volatility", volatility);
                    out.put("asOf", Instant.now().toString());

                    // put in cache
                    cache.put(cacheKey, new CacheEntry(out, System.currentTimeMillis() + cacheTtlMs));

                    writeJson(resp, 200, out);
                    log(req, coin, vs, ms(upstreamStart), 200, out, false, null, start);
                    return;
                } else {
                    err = "invalid upstream data";
                }
            } else {
                err = "upstream status " + upstreamStatus;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            err = "interrupted";
        } catch (Exception e) {
            err = "exception: " + e.getClass().getSimpleName();
        }

        // Error path
        Map<String, Object> errBody = Map.of("error", err);
        writeJson(resp, 502, errBody);
        log(req, coin, vs, ms(upstreamStart), upstreamStatus, null, false, err, start);
    }

    private void writeJson(HttpServletResponse resp, int status, Map<String, Object> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        try (PrintWriter w = resp.getWriter()) {
            w.write(mapper.writeValueAsString(body));
        }
    }

    private String volatility(double changePct) {
        double abs = Math.abs(changePct);
        if (abs < 2) return "calm";
        if (abs < 5) return "moderate";
        return "volatile";
    }

    private void log(HttpServletRequest req, String coin, String vs,
                     long upstreamLatencyMs, int upstreamStatus,
                     Map<String,Object> result, boolean cacheHit, String error,
                     long startNano) {
        try {
            Document log = new Document("ts", Instant.now().toString())
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

            logs.insertOne(log);
        } catch (Exception ignore) {
            // Logging should never crash the service
        }
    }

    private static String header(HttpServletRequest req, String name) {
        return Optional.ofNullable(req.getHeader(name)).orElse("");
    }
    private static long ms(long startNano){ return (System.nanoTime() - startNano)/1_000_000; }
    private static String opt(String s){ return s == null ? "" : s; }
}
