package org.example.linking;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExternalLinker {
    private static final Logger logger = LoggerFactory.getLogger(ExternalLinker.class);
    private static final String WIKIDATA_SPARQL_ENDPOINT = "https://query.wikidata.org/sparql";
    private final HttpClient httpClient;

    public ExternalLinker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
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

            String wikidataUri = findWikidataResource(pokemonName);
            if (wikidataUri != null) {
                pokemon.addProperty(OWL.sameAs, 
                    model.createResource(wikidataUri));
                String dbpediaUri = wikidataToDBpedia(wikidataUri);
                if (dbpediaUri != null) {
                    pokemon.addProperty(OWL.sameAs, 
                        model.createResource(dbpediaUri));
                }
                logger.info("Added external links for {}", pokemonName);
            }
        }
    }

    private String findWikidataResource(String pokemonName) {
        String query = String.format(
            "SELECT ?item WHERE {" +
            "  ?item rdfs:label \"%s\"@en ." +
            "  ?item wdt:P31/wdt:P279* wd:Q1420 . # instance of Pokemon" +
            "}", pokemonName);

        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WIKIDATA_SPARQL_ENDPOINT + "?query=" + encodedQuery))
                .header("Accept", "application/sparql-results+json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONArray bindings = json.getJSONObject("results")
                                      .getJSONArray("bindings");
                if (!bindings.isEmpty()) {
                    return bindings.getJSONObject(0)
                                 .getJSONObject("item")
                                 .getString("value");
                }
            }
        } catch (Exception e) {
            logger.warn("Error finding Wikidata resource: {}", e.getMessage());
        }
        return null;
    }

    private String wikidataToDBpedia(String wikidataUri) {
        // Convert Wikidata URI to DBpedia URI
        // Example: http://www.wikidata.org/entity/Q2859 -> http://dbpedia.org/resource/Bulbasaur
        String wikidataId = wikidataUri.substring(wikidataUri.lastIndexOf("/") + 1);
        String query = String.format(
            "SELECT ?article WHERE {" +
            "  wd:%s wdt:P1551 ?article . " + // P1551 is the property for DBpedia ID
            "}", wikidataId);

        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WIKIDATA_SPARQL_ENDPOINT + "?query=" + encodedQuery))
                .header("Accept", "application/sparql-results+json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONArray bindings = json.getJSONObject("results")
                                      .getJSONArray("bindings");
                if (!bindings.isEmpty()) {
                    String dbpediaId = bindings.getJSONObject(0)
                                             .getJSONObject("article")
                                             .getString("value");
                    return "http://dbpedia.org/resource/" + dbpediaId;
                }
            }
        } catch (Exception e) {
            logger.warn("Error converting Wikidata to DBpedia: {}", e.getMessage());
        }
        return null;
    }
}
