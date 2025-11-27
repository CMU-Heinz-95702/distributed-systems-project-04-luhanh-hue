package ds.task1;


import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FetchFromCoinGecko {
    private static final String BASE =
            "https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=%s&include_24hr_change=true";

    public static void main(String[] args) throws Exception {
        // Defaults so you can just run it without args
        String coin = args.length > 0 ? args[0] : "bitcoin";
        String vs   = args.length > 1 ? args[1] : "usd";

        String url = String.format(
                BASE,
                URLEncoder.encode(coin, StandardCharsets.UTF_8),
                URLEncoder.encode(vs, StandardCharsets.UTF_8)
        );

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            System.out.println("Error: upstream status " + res.statusCode());
            System.out.println("Body: " + res.body());
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(res.body());

        if (!root.has(coin) || !root.get(coin).has(vs)) {
            System.out.println("Unexpected response: " + res.body());
            System.exit(1);
        }

        double price = root.get(coin).get(vs).asDouble();
        JsonNode changeNode = root.get(coin).get(vs + "_24h_change");
        double change = (changeNode != null && !changeNode.isNull()) ? changeNode.asDouble() : 0.0;

        System.out.println("Coin: " + coin);
        System.out.println("Currency: " + vs);
        System.out.println("Current Price: " + price);
        System.out.printf("24h Change: %.2f%%%n", change);
    }
}