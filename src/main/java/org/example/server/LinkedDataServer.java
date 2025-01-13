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
            Path templatePath = Paths.get("templates", "pokemon.html");
            if (Files.exists(templatePath)) {
                htmlTemplate = Files.readString(templatePath);
            } else {
                // Create templates directory if it doesn't exist
                Files.createDirectories(Paths.get("templates"));
                // Write the default template
                Files.writeString(templatePath, buildDefaultTemplate());
                htmlTemplate = buildDefaultTemplate();
            }
        } catch (IOException e) {
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
            response.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
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
        Model description = fetchRdfDescription(resourceUri);
        if (description.isEmpty()) {
            return "# Resource not found";
        }
        
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, description, Lang.TURTLE);
        return writer.toString();
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
          }
      }
      
      // Add evolution chain data
      if (!data.isEmpty()) {
          addEvolutionData(data, resourceUri);
      }
      return data;
  }

  private String formatDecimal(double value) {
      return String.format("%.1f", value);
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

    // private void addExternalLinks(Map<String, Object> data, String resourceUri) {
    //     String query = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
    //                   "PREFIX schema: <http://schema.org/>\n" +
    //                   "SELECT ?dbpedia ?wikidata WHERE {\n" +
    //                   "  <" + resourceUri + "> owl:sameAs ?dbpedia, ?wikidata .\n" +
    //                   "  FILTER(CONTAINS(STR(?dbpedia), 'dbpedia.org'))\n" +
    //                   "  FILTER(CONTAINS(STR(?wikidata), 'wikidata.org'))\n" +
    //                   "}";
                      
    //     try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
    //         ResultSet results = qexec.execSelect();
    //         if (results.hasNext()) {
    //             QuerySolution solution = results.next();
    //             data.put("dbpediaLink", solution.getResource("dbpedia").getURI());
    //             data.put("wikidataLink", solution.getResource("wikidata").getURI());
    //         }
    //     }
    // }

    private Model fetchRdfDescription(String resourceUri) {
        String constructQuery = "CONSTRUCT { <" + resourceUri + "> ?p ?o .\n" +
                              "            ?s pokemon:evolvesFrom <" + resourceUri + "> .\n" +
                              "            <" + resourceUri + "> pokemon:evolvesFrom ?prev }\n" +
                              "WHERE {\n" +
                              "  { <" + resourceUri + "> ?p ?o }\n" +
                              "  UNION\n" +
                              "  { ?s pokemon:evolvesFrom <" + resourceUri + "> }\n" +
                              "  UNION\n" +
                              "  { <" + resourceUri + "> pokemon:evolvesFrom ?prev }\n" +
                              "}";

        try (QueryExecution qexec = QueryExecutionFactory.create(constructQuery, dataset)) {
            return qexec.execConstruct();
        }
    }

    private String renderTemplate(Map<String, Object> data) {
        String html = htmlTemplate;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            html = html.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return html;
    }

    public void stop() {
        Spark.stop();
        logger.info("Linked Data interface stopped");
    }
}
