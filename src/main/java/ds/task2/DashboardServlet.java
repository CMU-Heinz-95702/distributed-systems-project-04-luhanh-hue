package ds.task2;

import com.mongodb.client.*;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.Arrays.asList;

public class DashboardServlet extends HttpServlet {
    private MongoCollection<Document> logs;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        String envVar = Optional.ofNullable(config.getInitParameter("ATLAS_URI_ENV")).orElse("ATLAS_URI");
        String uri = System.getenv(envVar);
        if (uri == null || uri.isBlank()) {
            throw new ServletException("Missing MongoDB Atlas URI in env var: " + envVar);
        }
        MongoClient client = MongoClients.create(uri);
        logs = client.getDatabase("cryptoapp").getCollection("logs");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // NOTE: do NOT log dashboard hits (assignment requirement)
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        // 1) Top 10 coins by request count
        List<Document> topCoins = logs.aggregate(asList(
                Aggregates.match(Filters.gte("ts", since.toString())),
                Aggregates.group("$req.coin", Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.descending("count")),
                Aggregates.limit(10)
        )).into(new ArrayList<>());

        // 2) Avg upstream latency per coin (ms) in last 24h
        List<Document> avgLatency = logs.aggregate(asList(
                Aggregates.match(Filters.and(
                        Filters.gte("ts", since.toString()),
                        Filters.exists("upstream.latencyMs", true)
                )),
                Aggregates.group("$req.coin",
                        Accumulators.avg("avgLatencyMs", "$upstream.latencyMs")),
                Aggregates.sort(Sorts.ascending("_id"))
        )).into(new ArrayList<>());

        // 3) Error rate last 24h = (#status != 200) / total
        long tot = logs.countDocuments(Filters.gte("ts", since.toString()));
        long err = logs.countDocuments(Filters.and(
                Filters.gte("ts", since.toString()),
                Filters.ne("status", 200)
        ));
        double errorRate = (tot == 0) ? 0.0 : (err * 100.0 / tot);

        // 4) Recent logs (latest 50), formatted
        List<Document> recent = logs.find()
                .sort(Sorts.descending("ts"))
                .limit(50)
                .into(new ArrayList<>());

        // Put into request scope and forward to JSP
        req.setAttribute("topCoins", topCoins);
        req.setAttribute("avgLatency", avgLatency);
        req.setAttribute("errorRate", String.format(Locale.US, "%.2f%%", errorRate));
        req.setAttribute("sinceIso", since.toString());
        req.setAttribute("recent", recent);
        req.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(req, resp);
    }
}
