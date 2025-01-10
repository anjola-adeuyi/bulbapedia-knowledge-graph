package org.example.client;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class BulbapediaClient {
    private static final String API_ENDPOINT = "https://bulbapedia.bulbagarden.net/w/api.php";
    private final HttpClient httpClient;

    public BulbapediaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JSONObject getPageContent(String pageTitle) throws IOException, InterruptedException {
        String url = API_ENDPOINT + "?action=parse" +
                "&page=" + java.net.URLEncoder.encode(pageTitle, "UTF-8") +
                "&prop=wikitext|templates|images|links" +
                "&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject queryAllPages(String continueFrom) throws IOException, InterruptedException {
        String url = API_ENDPOINT + "?action=query" +
                "&list=allpages" +
                "&aplimit=500" +
                (continueFrom != null ? "&apcontinue=" + continueFrom : "") +
                "&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "BulbapediaKGBot/1.0 (pokemon.kg@example.com)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    public JSONObject getInfobox(String pageTitle) throws IOException, InterruptedException {
        JSONObject content = getPageContent(pageTitle);
        // Extract and return infobox template if it exists
        // Implementation will need parsing logic for wiki templates
        return content;
    }
}
