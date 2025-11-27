<%@ page import="java.util.*, org.bson.Document" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <title>Crypto Service – Ops Dashboard</title>
  <style>
    body { font-family: system-ui, Arial, sans-serif; margin: 24px; }
    h1,h2 { margin: 0 0 12px 0; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0 24px; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background: #f4f4f4; }
    .pill { display: inline-block; padding: 2px 8px; border-radius: 12px; background: #eef; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
  </style>
</head>
<body>
<h1>Crypto Service – Operations Dashboard</h1>
<div>Window: last 24h since <span class="mono"><%= request.getAttribute("sinceIso") %></span></div>
<div>Current error rate: <strong><%= request.getAttribute("errorRate") %></strong></div>

<h2>Top 10 Coins</h2>
<table>
  <tr><th>Coin</th><th>Requests</th></tr>
  <%
    List<Document> topCoins = (List<Document>) request.getAttribute("topCoins");
    if (topCoins != null) {
      for (Document d : topCoins) {
  %>
  <tr>
    <td><%= d.getString("_id") %></td>
    <td><%= d.get("count") %></td>
  </tr>
  <%
      }
    }
  %>
</table>

<h2>Average Upstream Latency (ms) per Coin</h2>
<table>
  <tr><th>Coin</th><th>Avg Latency (ms)</th></tr>
  <%
    List<Document> avgLatency = (List<Document>) request.getAttribute("avgLatency");
    if (avgLatency != null) {
      for (Document d : avgLatency) {
        String coin = d.getString("_id");
        Number msNum = (Number) d.get("avgLatencyMs");           // cast to Number
        String msStr = (msNum == null) ? "" : String.format(Locale.US, "%.1f", msNum.doubleValue());
  %>
  <tr>
    <td><%= coin %></td>
    <td><%= msStr %></td>
  </tr>
  <%
      }
    }
  %>
</table>

<h2>Recent Requests (latest 50)</h2>
<table>
  <tr>
    <th>Time</th><th>Coin</th><th>VS</th><th>Price</th>
    <th>Δ24h (%)</th><th>Volatility</th>
    <th>Upstream ms</th><th>Status</th><th>Device</th><th>Cache</th>
  </tr>
  <%
    List<Document> recent = (List<Document>) request.getAttribute("recent");
    if (recent != null) {
      for (Document r : recent) {
        Document reqDoc = (Document) r.get("req");
        Document up = (Document) r.get("upstream");
        Document result = (Document) r.get("result");
        Document dev = (Document) r.get("device");

        String ts = String.valueOf(r.get("ts"));
        String coin = reqDoc == null ? "" : String.valueOf(reqDoc.get("coin"));
        String vs = reqDoc == null ? "" : String.valueOf(reqDoc.get("vs"));
        String price = result == null ? "" : String.valueOf(result.get("price"));
        String change = result == null ? "" : String.valueOf(result.get("change24h_pct"));
        String vol = result == null ? "" : String.valueOf(result.get("volatility"));
        String lat = up == null ? "" : String.valueOf(up.get("latencyMs"));
        String st = String.valueOf(r.get("status"));
        String devModel = dev == null ? "" : String.valueOf(dev.get("model"));
        String cacheHit = String.valueOf(r.get("cacheHit"));
  %>
  <tr>
    <td class="mono"><%= ts %></td>
    <td><%= coin %></td>
    <td><%= vs %></td>
    <td><%= price %></td>
    <td><%= change %></td>
    <td><span class="pill"><%= vol %></span></td>
    <td><%= lat %></td>
    <td><%= st %></td>
    <td><%= devModel %></td>
    <td><%= cacheHit %></td>
  </tr>
  <%
      }
    }
  %>
</table>
</body>
</html>

