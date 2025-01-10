package org.example.client;

import org.json.JSONObject;
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
    private static final String API_ENDPOINT = "https://bulbapedia.bulbagarden.net/w/api.php";
    private final HttpClient httpClient;

    public BulbapediaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
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
            "prop", "wikitext|templates|images|links",
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

    public JSONObject queryAllPages(String continueFrom) throws IOException, InterruptedException {
        Map<String, String> params = Map.of(
            "action", "query",
            "list", "allpages",
            "aplimit", "500",
            "format", "json",
            "apcontinue", continueFrom != null ? continueFrom : ""
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(params)))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject getInfobox(String pageTitle) throws IOException, InterruptedException {
        return getPageContent(pageTitle);
    }
}
