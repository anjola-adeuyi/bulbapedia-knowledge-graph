package org.example.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndpointTester {
    private static final Logger logger = LoggerFactory.getLogger(EndpointTester.class);
    private final HttpClient httpClient;
    private final String baseUrl;

    public EndpointTester(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void testEndpoints() {
        try {
            // Test simple query
            String query = "PREFIX schema: <http://schema.org/>\n" +
                         "SELECT ?name WHERE { ?s schema:name ?name }";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/query"))
                    .header("Content-Type", "application/sparql-query")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            logger.info("Query response code: " + response.statusCode());
            logger.info("Query response: " + response.body());
            
        } catch (IOException | InterruptedException e) {
            logger.error("Error testing endpoints:", e);
        }
    }
}
