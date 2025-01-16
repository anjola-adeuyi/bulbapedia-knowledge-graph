package org.example.client;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public class BulbapediaClient {
    private static final Logger logger = LoggerFactory.getLogger(BulbapediaClient.class);
    private static final String API_ENDPOINT = "https://bulbapedia.bulbagarden.net/w/api.php";
    private final HttpClient httpClient;

    public BulbapediaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    private String buildUrl(Map<String, String> params) {
        String queryString = params.entrySet().stream()
            .map(e -> {
                try {
                    return URLEncoder.encode(e.getKey(), "UTF-8") + "=" + 
                           URLEncoder.encode(e.getValue(), "UTF-8");
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            })
            .collect(Collectors.joining("&"));
        return API_ENDPOINT + "?" + queryString;
    }

    public JSONObject getPageContent(String pageTitle) throws IOException, InterruptedException {
        Map<String, String> params = Map.of(
            "action", "parse",
            "page", pageTitle,
            "prop", "wikitext|categories|templates",
            "format", "json"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(params)))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject queryCategory(String category, String continueFrom) throws IOException, InterruptedException {
        Map<String, String> params;
        if (continueFrom != null) {
            params = Map.of(
                "action", "query",
                "list", "categorymembers",
                "cmtitle", "Category:" + category,
                "cmlimit", "500",
                "format", "json",
                "cmcontinue", continueFrom
            );
        } else {
            params = Map.of(
                "action", "query",
                "list", "categorymembers",
                "cmtitle", "Category:" + category,
                "cmlimit", "500",
                "format", "json"
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(params)))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject searchPages(String query) throws IOException, InterruptedException {
        Map<String, String> params = Map.of(
            "action", "query",
            "list", "search",
            "srsearch", query,
            "format", "json"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(params)))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject getTemplates(String pageTitle) throws IOException, InterruptedException {
        Map<String, String> params = Map.of(
            "action", "parse",
            "page", pageTitle,
            "prop", "templates",
            "format", "json"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(params)))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }
}
