package org.example.server;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LinkedDataServer {
    private static final Logger logger = LoggerFactory.getLogger(LinkedDataServer.class);
    private final Dataset dataset;
    private final int port;
    private String htmlTemplate;

    public LinkedDataServer(Dataset dataset, int port) {
        this.dataset = dataset;
        this.port = port;
        loadTemplate();
    }

    private void loadTemplate() {
        try {
            // Try to load from resources first
            var classLoader = getClass().getClassLoader();
            var templateUrl = classLoader.getResource("templates/pokemon.html");
            
            if (templateUrl != null) {
                htmlTemplate = new String(Files.readAllBytes(Paths.get(templateUrl.toURI())));
                logger.info("Loaded HTML template from resources");
            } else {
                // Try to load from file system
                Path templatePath = Paths.get("src/main/resources/templates/pokemon.html");
                if (Files.exists(templatePath)) {
                    htmlTemplate = Files.readString(templatePath);
                    logger.info("Loaded HTML template from file system");
                } else {
                    logger.warn("HTML template not found, using default template");
                    htmlTemplate = buildDefaultTemplate();
                }
            }
        } catch (Exception e) {
            logger.error("Error loading HTML template:", e);
            htmlTemplate = buildDefaultTemplate();
        }
    }

    private String buildDefaultTemplate() {
        return "<!DOCTYPE html><html><body><h1>${name} #${id}</h1></body></html>";
    }

    public void start() {
        Spark.port(port);
        
        // Set up CORS headers
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", 
                          "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
        });

        // Handle content negotiation
        Spark.get("/resource/:id", this::handleResourceRequest);
        
        logger.info("Linked Data interface started on port {}", port);
    }

    private Object handleResourceRequest(Request request, Response response) {
        String id = request.params(":id");
        String resourceUri = "http://example.org/pokemon/pokemon/" + id;
        String accept = request.headers("Accept");

        // Content negotiation
        if (accept != null && accept.contains("text/html")) {
            response.type("text/html");
            return createHtmlResponse(resourceUri);
        } else {
            response.type("text/turtle");
            return createRdfResponse(resourceUri);
        }
    }

    private String createHtmlResponse(String resourceUri) {
        Map<String, Object> pokemonData = fetchPokemonData(resourceUri);
        if (pokemonData.isEmpty()) {
            return "<html><body><h1>404 - Pokemon Not Found</h1></body></html>";
        }
        return renderTemplate(pokemonData);
    }

    private String createRdfResponse(String resourceUri) {
      String query = 
          "CONSTRUCT { <" + resourceUri + "> ?p ?o . " +
          "            ?s pokemon:evolvesFrom <" + resourceUri + "> . " +
          "            <" + resourceUri + "> pokemon:evolvesFrom ?prev }" +
          "WHERE { " +
          "  { <" + resourceUri + "> ?p ?o } " +
          "  UNION " +
          "  { ?s pokemon:evolvesFrom <" + resourceUri + "> } " +
          "  UNION " +
          "  { <" + resourceUri + "> pokemon:evolvesFrom ?prev } " +
          "}";

      try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
          Model description = qexec.execConstruct();
          if (description.isEmpty()) {
              return "# Resource not found";
          }
          StringWriter writer = new StringWriter();
          RDFDataMgr.write(writer, description, Lang.TURTLE);
          return writer.toString();
      }
  }

    private Map<String, Object> fetchPokemonData(String resourceUri) {
        String query = 
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "PREFIX schema: <http://schema.org/>\n" +
            "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "SELECT DISTINCT ?name ?id ?primaryType ?secondaryType ?height ?weight " +
            "?category ?japaneseName ?romajiName ?dbpedia ?wikidata\n" +
            "WHERE {\n" +
            "  BIND(<" + resourceUri + "> AS ?pokemon)\n" +
            "  ?pokemon schema:name ?name ;\n" +
            "          schema:identifier ?id ;\n" +
            "          pokemon:primaryType ?primaryType ;\n" +
            "          schema:height ?height ;\n" +
            "          schema:weight ?weight ;\n" +
            "          pokemon:category ?category .\n" +
            "  OPTIONAL { ?pokemon pokemon:secondaryType ?secondaryType }\n" +
            "  OPTIONAL { ?pokemon pokemon:japaneseName ?japaneseName }\n" +
            "  OPTIONAL { ?pokemon pokemon:romajiName ?romajiName }\n" +
            "  OPTIONAL { ?pokemon owl:sameAs ?dbpedia .\n" +
            "            FILTER(CONTAINS(STR(?dbpedia), 'dbpedia.org')) }\n" +
            "  OPTIONAL { ?pokemon owl:sameAs ?wikidata .\n" +
            "            FILTER(CONTAINS(STR(?wikidata), 'wikidata.org')) }\n" +
            "}\n";

        Map<String, Object> data = new HashMap<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                data.put("name", solution.getLiteral("name").getString());
                data.put("id", solution.getLiteral("id").getString());
                data.put("primaryType", solution.getLiteral("primaryType").getString());
                data.put("height", formatDecimal(solution.getLiteral("height").getDouble()));
                data.put("weight", formatDecimal(solution.getLiteral("weight").getDouble()));
                data.put("category", solution.getLiteral("category").getString());
                
                if (solution.contains("secondaryType")) {
                    data.put("secondaryType", solution.getLiteral("secondaryType").getString());
                }
                if (solution.contains("japaneseName")) {
                    data.put("japaneseName", solution.getLiteral("japaneseName").getString());
                }
                if (solution.contains("romajiName")) {
                    data.put("romajiName", solution.getLiteral("romajiName").getString());
                }
                if (solution.contains("dbpedia")) {
                    data.put("dbpediaLink", solution.getResource("dbpedia").getURI());
                }
                if (solution.contains("wikidata")) {
                    data.put("wikidataLink", solution.getResource("wikidata").getURI());
                }

                // Add evolution chain data
                addEvolutionData(data, resourceUri);
            }
        }
        
        return data;
    }

    private void addEvolutionData(Map<String, Object> data, String resourceUri) {
        // Query for previous evolution
        String prevQuery = 
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "PREFIX schema: <http://schema.org/>\n" +
            "SELECT ?name ?id WHERE {\n" +
            "  <" + resourceUri + "> pokemon:evolvesFrom ?prev .\n" +
            "  ?prev schema:name ?name ;\n" +
            "        schema:identifier ?id .\n" +
            "}\n";
            
        try (QueryExecution qexec = QueryExecutionFactory.create(prevQuery, dataset)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                Map<String, String> prevPokemon = new HashMap<>();
                prevPokemon.put("name", solution.getLiteral("name").getString());
                prevPokemon.put("id", solution.getLiteral("id").getString());
                data.put("prevPokemon", prevPokemon);
            }
        }

        // Query for next evolution
        String nextQuery = 
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "PREFIX schema: <http://schema.org/>\n" +
            "SELECT ?name ?id WHERE {\n" +
            "  ?next pokemon:evolvesFrom <" + resourceUri + "> ;\n" +
            "        schema:name ?name ;\n" +
            "        schema:identifier ?id .\n" +
            "}\n";
            
        try (QueryExecution qexec = QueryExecutionFactory.create(nextQuery, dataset)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                Map<String, String> nextPokemon = new HashMap<>();
                nextPokemon.put("name", solution.getLiteral("name").getString());
                nextPokemon.put("id", solution.getLiteral("id").getString());
                data.put("nextPokemon", nextPokemon);
            }
        }
    }

    private String formatDecimal(double value) {
        return String.format("%.1f", value);
    }

    private String renderTemplate(Map<String, Object> data) {
        String result = htmlTemplate;

        // Handle simple properties
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (!(entry.getValue() instanceof Map)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        // Handle previous pokemon
        @SuppressWarnings("unchecked")
        Map<String, String> prevPokemon = (Map<String, String>) data.get("prevPokemon");
        String prevSection = prevPokemon != null ?
            "<a href=\"/resource/" + prevPokemon.get("id") + "\" class=\"pokemon-card\">" +
            "<h3>" + prevPokemon.get("name") + "</h3>" +
            "<p>#" + prevPokemon.get("id") + "</p>" +
            "</a>" +
            "<span class=\"evolution-arrow\">→</span>" : "";
        result = result.replace("${prevPokemonSection}", prevSection);

        // Handle next pokemon
        @SuppressWarnings("unchecked")
        Map<String, String> nextPokemon = (Map<String, String>) data.get("nextPokemon");
        String nextSection = nextPokemon != null ?
            "<span class=\"evolution-arrow\">→</span>" +
            "<a href=\"/resource/" + nextPokemon.get("id") + "\" class=\"pokemon-card\">" +
            "<h3>" + nextPokemon.get("name") + "</h3>" +
            "<p>#" + nextPokemon.get("id") + "</p>" +
            "</a>" : "";
        result = result.replace("${nextPokemonSection}", nextSection);

        return result;
    }

    public void stop() {
        Spark.stop();
    }
}
