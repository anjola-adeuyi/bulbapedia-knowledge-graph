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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ExternalLinker {
    private static final Logger logger = LoggerFactory.getLogger(ExternalLinker.class);
    private static final String WIKIDATA_ENDPOINT = "https://query.wikidata.org/sparql";
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

            try {
                // First get the Wikidata URI
                String wikidataUri = findWikidataResource(pokemonName);
                if (wikidataUri != null) {
                    pokemon.addProperty(OWL.sameAs, 
                        model.createResource(wikidataUri));
                    logger.info("Added Wikidata link for {}: {}", 
                        pokemonName, wikidataUri);
                    
                    // Get DBpedia URI from Wikidata
                    String dbpediaUri = wikidataToDBpedia(wikidataUri);
                    if (dbpediaUri != null) {
                        pokemon.addProperty(OWL.sameAs,
                            model.createResource(dbpediaUri));
                        logger.info("Added DBpedia link for {}: {}",
                            pokemonName, dbpediaUri);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error adding external links for {}: {}", 
                    pokemonName, e.getMessage());
            }
        }
    }

    private String findWikidataResource(String pokemonName) {
        String query = "SELECT ?item WHERE {" +
                      "  ?item rdfs:label \"" + pokemonName + "\"@en ." +
                      "  ?item wdt:P31 wd:Q3966183 ." + // instance of Pokemon
                      "  SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\" }" +
                      "}";

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WIKIDATA_ENDPOINT + "?query=" + encodedQuery))
                .header("Accept", "application/sparql-results+json")
                .header("User-Agent", "PokemonKGBuilder/1.0")
                .GET()
                .build();

            logger.debug("Querying Wikidata for Pokemon: {}", pokemonName);
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
            logger.debug("Wikidata response: {}", response.body());
        } catch (Exception e) {
            logger.warn("Error querying Wikidata: {}", e.getMessage());
        }
        return null;
    }

    private String wikidataToDBpedia(String wikidataUri) {
        // Extract Wikidata ID
        String wikidataId = wikidataUri.substring(wikidataUri.lastIndexOf("/") + 1);
        String query = "SELECT ?article WHERE {" +
                      "  wd:" + wikidataId + " wdt:P1889 ?article ." + // DBpedia external link
                      "}";

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WIKIDATA_ENDPOINT + "?query=" + encodedQuery))
                .header("Accept", "application/sparql-results+json")
                .header("User-Agent", "PokemonKGBuilder/1.0")
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
                                 .getJSONObject("article")
                                 .getString("value");
                }
            }
            logger.debug("DBpedia lookup response: {}", response.body());
        } catch (Exception e) {
            logger.warn("Error converting to DBpedia URI: {}", e.getMessage());
        }
        return null;
    }
}
