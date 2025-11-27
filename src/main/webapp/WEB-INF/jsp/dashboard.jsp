<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*, org.bson.Document" %>
<html>
<head>
  <title>Crypto Service Dashboard</title>
</head>
<body>
<h1>Crypto Service Dashboard</h1>

<p>Window since: ${sinceIso}</p>
<p>Error rate (last 24h): ${errorRate}</p>

<hr/>

<h2>Top Coins (last 24h)</h2>
<table border="1" cellpadding="4">
  <tr><th>Coin</th><th>Requests</th></tr>
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
  <%
    }
  } else {
  %>
  <tr><td colspan="2">No data</td></tr>
  <%
    }
  %>
</table>

<hr/>

<h2>Average Upstream Latency (ms) per Coin</h2>
<table border="1" cellpadding="4">
  <tr><th>Coin</th><th>Avg Latency (ms)</th></tr>
  <%
    List<Document> avgLatency = (List<Document>) request.getAttribute("avgLatency");
    if (avgLatency != null) {
      for (Document d : avgLatency) {
        String coin = String.valueOf(d.get("_id"));
        Object latency = d.get("avgLatencyMs");
  %>
  <tr>
    <td><%= coin %></td>
    <td><%= latency %></td>
  </tr>
  <%
    }
  } else {
  %>
  <tr><td colspan="2">No data</td></tr>
  <%
    }
  %>
</table>

<hr/>

<h2>Most Recent Logs (latest 50)</h2>
<%
  List<Document> recent = (List<Document>) request.getAttribute("recent");
  if (recent != null && !recent.isEmpty()) {
%>
<table border="1" cellpadding="4">
  <tr>
    <th>Time</th><th>Coin</th><th>VS</th>
    <th>Status</th><th>Cache?</th><th>Error</th>
  </tr>
  <%
    for (Document d : recent) {
      String ts = String.valueOf(d.get("ts"));
      Document reqDoc = (Document) d.get("req");
      Document upstream = (Document) d.get("upstream");
      String coin = reqDoc != null ? String.valueOf(reqDoc.get("coin")) : "";
      String vs = reqDoc != null ? String.valueOf(reqDoc.get("vs")) : "";
      Object status = d.get("status");
      Object cacheHit = d.get("cacheHit");
      Object error = d.get("error");
  %>
  <tr>
    <td><%= ts %></td>
    <td><%= coin %></td>
    <td><%= vs %></td>
    <td><%= status %></td>
    <td><%= cacheHit %></td>
    <td><%= error %></td>
  </tr>
  <%
    }
  %>
</table>
<%
} else {
%>
<p>No recent logs found.</p>
<%
  }
%>

</body>
</html>

