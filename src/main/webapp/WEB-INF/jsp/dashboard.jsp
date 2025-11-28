<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="org.bson.Document" %>

<html>
<head>
  <title>Crypto Service Dashboard</title>
  <style>
    body { font-family: sans-serif; margin: 20px; }
    h1 { color: #1e88e5; }
    table { border-collapse: collapse; margin-bottom: 24px; width: 100%; }
    th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }
    th { background-color: #f5f5f5; }
    .section-title { margin-top: 30px; }
    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; }
    .badge-ok { background: #c8e6c9; }
    .badge-error { background: #ffcdd2; }
  </style>
</head>
<body>
<h1>Crypto Service Dashboard</h1>

<p>
  Window since:
  <strong><%= request.getAttribute("sinceIso") %></strong>
</p>
<p>
  Error rate (last 24h):
  <strong><%= request.getAttribute("errorRate") %></strong>
</p>

<h2 class="section-title">Top Coins (last 24h)</h2>
<table>
  <tr>
    <th>Coin</th>
    <th>Requests</th>
  </tr>
  <%
    List<Document> topCoins = (List<Document>) request.getAttribute("topCoins");
    if (topCoins != null) {
      for (Document d : topCoins) {
        String coin = String.valueOf(d.get("_id"));
        Object count = d.get("count");
  %>
  <tr>
    <td><%= coin %></td>
    <td><%= count %></td>
  </tr>
  <%      }
  } %>
</table>

<h2 class="section-title">Average Upstream Latency (ms) per Coin</h2>
<table>
  <tr>
    <th>Coin</th>
    <th>Avg Latency (ms)</th>
  </tr>
  <%
    List<Document> avgLatency = (List<Document>) request.getAttribute("avgLatency");
    if (avgLatency != null) {
      for (Document d : avgLatency) {
        String coin = String.valueOf(d.get("_id"));
        Object avg = d.get("avgLatencyMs");
  %>
  <tr>
    <td><%= coin %></td>
    <td><%= avg %></td>
  </tr>
  <%      }
  } %>
</table>

<h2 class="section-title">Most Recent Logs (latest 50)</h2>
<table>
  <tr>
    <th>Time</th>
    <th>Coin</th>
    <th>VS</th>
    <th>Status</th>
    <th>Cache?</th>
    <th>Error</th>
  </tr>
  <%
    List<Document> recent = (List<Document>) request.getAttribute("recent");
    if (recent != null) {
      for (Document log : recent) {
        String ts = String.valueOf(log.get("ts"));
        Document reqDoc = (Document) log.get("req");
        String coin = reqDoc == null ? "" : String.valueOf(reqDoc.get("coin"));
        String vs = reqDoc == null ? "" : String.valueOf(reqDoc.get("vs"));
        Object status = log.get("status");
        Boolean cacheHit = (Boolean) log.get("cacheHit");
        String error = String.valueOf(log.get("error"));
  %>
  <tr>
    <td><%= ts %></td>
    <td><%= coin %></td>
    <td><%= vs %></td>
    <td>
            <span class="badge <%= (status != null && status.equals(200)) ? "badge-ok" : "badge-error" %>">
                <%= status %>
            </span>
    </td>
    <td><%= cacheHit == null ? "" : cacheHit %></td>
    <td><%= "null".equals(error) ? "" : error %></td>
  </tr>
  <%      }
  } %>
</table>

</body>
</html>


