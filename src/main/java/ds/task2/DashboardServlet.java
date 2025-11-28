/**
 * Author: Luhan Huang
 * AndrewID: luhanh
 * Course: 95-702 Distributed Systems
 * Project 4
 *
 * This file is part of my submission for Project .
 */
package ds.task2;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;

public class DashboardServlet extends HttpServlet {

    private MongoCollection<Document> logs;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String envVar = config.getInitParameter("ATLAS_URI_ENV");
        if (envVar == null || envVar.isBlank()) {
            envVar = "ATLAS_URI";
        }

        String uri = System.getenv(envVar);
        if (uri == null || uri.isBlank()) {
            throw new ServletException("Missing MongoDB Atlas URI in env var: " + envVar);
        }

        try {
            MongoClient client = MongoClients.create(uri);
            MongoDatabase db = client.getDatabase("cryptoapp");
            logs = db.getCollection("logs");
        } catch (Exception e) {
            throw new ServletException("Failed to connect to MongoDB: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // IMPORTANT: we do NOT log dashboard hits (per requirements).
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        // 1) Top 10 coins by request count
        List<Document> topCoins = logs.aggregate(asList(
                Aggregates.match(Filters.gte("ts", since.toString())),
                Aggregates.group("$req.coin", Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.descending("count")),
                Aggregates.limit(10)
        )).into(new ArrayList<>());

        // 2) Avg upstream latency per coin
        List<Document> avgLatency = logs.aggregate(asList(
                Aggregates.match(Filters.and(
                        Filters.gte("ts", since.toString()),
                        Filters.exists("upstream.latencyMs", true)
                )),
                Aggregates.group("$req.coin",
                        Accumulators.avg("avgLatencyMs", "$upstream.latencyMs")),
                Aggregates.sort(Sorts.ascending("_id"))
        )).into(new ArrayList<>());

        // 3) Error rate last 24h
        long total = logs.countDocuments(Filters.gte("ts", since.toString()));
        long errors = logs.countDocuments(Filters.and(
                Filters.gte("ts", since.toString()),
                Filters.ne("status", 200)
        ));
        double errorRate = (total == 0) ? 0.0 : (errors * 100.0 / total);

        // 4) Recent logs (latest 50)
        List<Document> recent = logs.find(Filters.gte("ts", since.toString()))
                .sort(Sorts.descending("ts"))
                .limit(50)
                .into(new ArrayList<>());

        req.setAttribute("sinceIso", since.toString());
        req.setAttribute("errorRate", String.format(Locale.US, "%.2f%%", errorRate));
        req.setAttribute("topCoins", topCoins);
        req.setAttribute("avgLatency", avgLatency);
        req.setAttribute("recent", recent);

        req.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(req, resp);
    }
}

