package org.example.linking;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExternalLinker {
    private static final Logger logger = LoggerFactory.getLogger(ExternalLinker.class);
    private static final String DBPEDIA_SPARQL_ENDPOINT = "https://dbpedia.org/sparql";
    private final HttpClient httpClient;

    public ExternalLinker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void addExternalLinks(Model model) {
        ResIterator pokemonIterator = model.listResourcesWithProperty(
            model.createProperty("http://schema.org/name"));

        while (pokemonIterator.hasNext()) {
            Resource pokemon = pokemonIterator.next();
            String pokemonName = pokemon.getProperty(
                model.createProperty("http://schema.org/name"))
                .getString();

            // Find DBpedia resource
            String dbpediaUri = findDBpediaResource(pokemonName);
            if (dbpediaUri != null) {
                pokemon.addProperty(OWL.sameAs, 
                    model.createResource(dbpediaUri));
                logger.info("Added DBpedia link for {}: {}", 
                    pokemonName, dbpediaUri);
            }
        }
    }

    private String findDBpediaResource(String pokemonName) {
        // Simpler query that just looks for resources with matching label
        String query = String.format(
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
            "SELECT DISTINCT ?resource WHERE {\n" +
            "  ?resource rdfs:label \"%s\"@en .\n" +
            "  FILTER EXISTS { ?resource ?p ?o }\n" +
            "} LIMIT 1",
            pokemonName);

        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://dbpedia.org/sparql" + "?query=" + encodedQuery + "&format=json"))
                .header("Accept", "application/sparql-results+json")
                .timeout(Duration.ofSeconds(10))  // Reduced timeout
                .GET()
                .build();

            logger.debug("Querying DBpedia for {}", pokemonName);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("DBpedia response status: {}", response.statusCode());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONArray bindings = json.getJSONObject("results")
                                      .getJSONArray("bindings");
                
                if (bindings.length() > 0) {
                    String uri = bindings.getJSONObject(0)
                                      .getJSONObject("resource")
                                      .getString("value");
                    logger.info("Found DBpedia resource for {}: {}", 
                              pokemonName, uri);
                    return uri;
                }
            } else {
                logger.warn("DBpedia query failed with status code: {} for {}", 
                          response.statusCode(), pokemonName);
                logger.debug("Response: {}", response.body());
            }
        } catch (Exception e) {
            logger.warn("Error finding DBpedia resource for {}: {}", 
                pokemonName, e.getMessage());
        }
        return null;
    }
}
