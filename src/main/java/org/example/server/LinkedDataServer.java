package org.example.server;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
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
        // Serve static files
        // Was facing issues initially with the path, so I realized that static files must be configured before routes
        Spark.staticFiles.location("/static");

        Spark.port(port);
        
        // Set up CORS headers
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", 
                          "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
        });

        // Add root route
        Spark.get("/", (req, res) -> {
            res.type("text/html");
            return readIndexHtml();
        });

        // Handle content negotiation
        Spark.get("/resource/:id", this::handleResourceRequest);
        
        logger.info("Linked Data interface started on port {}", port);
    }

    private String readIndexHtml() {
        try {
            InputStream is = getClass().getResourceAsStream("/static/index.html");
            if (is == null) {
                logger.error("Could not find index.html");
                return "<h1>Error: index.html not found</h1>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error reading index.html", e);
            return "<h1>Error loading page</h1>";
        }
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
        Map<String, Object> data = fetchPokemonData(resourceUri);
        if (data.isEmpty()) {
            String template = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>404 - Pokemon Not Found</title>\n" +
                "    <style>\n" +
                "        body { font-family: 'Segoe UI', system-ui, sans-serif; margin: 40px; }\n" +
                "        .error-container { text-align: center; padding: 40px; }\n" +
                "        h1 { color: #dc3545; margin-bottom: 20px; }\n" +
                "        .message { color: #6c757d; margin-bottom: 30px; }\n" +
                "        .back-link { color: #007bff; text-decoration: none; }\n" +
                "        .back-link:hover { text-decoration: underline; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"error-container\">\n" +
                "        <h1>404 - Pokemon Not Found</h1>\n" +
                "        <p class=\"message\">The Pokemon you're looking for doesn't exist in our database.</p>\n" +
                "        <a href=\"/resource/0001\" class=\"back-link\">← Start from Bulbasaur</a>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
            return template;
        }
        return renderTemplate(data);
    }

    private String createRdfResponse(String resourceUri) {
        try {
            String query = String.format(
                "CONSTRUCT { <%s> ?p ?o } WHERE { <%s> ?p ?o }",
                resourceUri, resourceUri
            );
            
            QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
            Model description = qexec.execConstruct();
            
            StringWriter writer = new StringWriter();
            description.write(writer, "TURTLE");
            return writer.toString();
        } catch (Exception e) {
            logger.error("Error creating RDF response:", e);
            return "# Error generating RDF";
        }
    }

    private Map<String, Object> fetchPokemonData(String resourceUri) {
        Map<String, Object> data = new HashMap<>();
        
        // First check if the Pokemon exists
        String checkQuery = 
            "ASK WHERE { <" + resourceUri + "> a <http://example.org/pokemon/Pokemon> }";
        
        try (QueryExecution qexec = QueryExecutionFactory.create(checkQuery, dataset)) {
            boolean exists = qexec.execAsk();
            if (!exists) {
                return data;
            }
        }

        // Existing query for Pokemon data
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
            "          pokemon:primaryType ?primaryType .\n" +
            "  OPTIONAL { ?pokemon schema:height ?height }\n" +
            "  OPTIONAL { ?pokemon schema:weight ?weight }\n" +
            "  OPTIONAL { ?pokemon pokemon:category ?category }\n" +
            "  OPTIONAL { ?pokemon pokemon:secondaryType ?secondaryType }\n" +
            "  OPTIONAL { ?pokemon pokemon:japaneseName ?japaneseName }\n" +
            "  OPTIONAL { ?pokemon pokemon:romajiName ?romajiName }\n" +
            "  OPTIONAL { ?pokemon owl:sameAs ?dbpedia .\n" +
            "            FILTER(CONTAINS(STR(?dbpedia), 'dbpedia.org')) }\n" +
            "  OPTIONAL { ?pokemon owl:sameAs ?wikidata .\n" +
            "            FILTER(CONTAINS(STR(?wikidata), 'wikidata.org')) }\n" +
            "}\n";

        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                data.put("name", solution.getLiteral("name").getString());
                data.put("id", solution.getLiteral("id").getString());
                data.put("primaryType", solution.getLiteral("primaryType").getString());

                if (solution.contains("height")) {
                    data.put("height", formatDecimal(solution.getLiteral("height").getDouble()));
                }
                if (solution.contains("weight")) {
                    data.put("weight", formatDecimal(solution.getLiteral("weight").getDouble()));
                }
                if (solution.contains("category")) {
                    data.put("category", solution.getLiteral("category").getString());
                }
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
        } catch (Exception e) {
            logger.error("Error fetching Pokemon data: ", e);
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
      if (data == null || data.isEmpty()) {
          // return createNotFoundPage();
          return null;
      }
  
      String result = htmlTemplate;
  
      // Safely get required fields with defaults
      String name = String.valueOf(data.getOrDefault("name", "Unknown"));
      String id = String.valueOf(data.getOrDefault("id", "0000"));
      String primaryType = String.valueOf(data.getOrDefault("primaryType", "Normal"));
      String height = String.valueOf(data.getOrDefault("height", "0.0"));
      String weight = String.valueOf(data.getOrDefault("weight", "0.0"));
      String category = String.valueOf(data.getOrDefault("category", "Unknown"));
  
      // Replace basic fields
      result = result.replace("${name}", name);
      result = result.replace("${id}", id);
      result = result.replace("${height}", height);
      result = result.replace("${weight}", weight);
      result = result.replace("${category}", category + " Pokemon");
  
      // Handle type badges
      StringBuilder typeHtml = new StringBuilder();
      typeHtml.append(String.format("<span class=\"type-badge\" style=\"background-color: var(--%s-color)\">%s</span>", 
          primaryType.toLowerCase(), primaryType));
      
      if (data.containsKey("secondaryType")) {
          String secondaryType = String.valueOf(data.get("secondaryType"));
          typeHtml.append(String.format("<span class=\"type-badge\" style=\"background-color: var(--%s-color)\">%s</span>", 
              secondaryType.toLowerCase(), secondaryType));
      }
      result = result.replace("${typeBadges}", typeHtml.toString());
  
      // Handle names section
      StringBuilder namesHtml = new StringBuilder();
      namesHtml.append(String.format("<div class=\"stat-row\"><span class=\"stat-label\">English</span><span>%s</span></div>", name));
      
      if (data.containsKey("japaneseName")) {
          namesHtml.append(String.format("<div class=\"stat-row\"><span class=\"stat-label\">Japanese</span><span>%s</span></div>", 
              data.get("japaneseName")));
      }
      if (data.containsKey("romajiName")) {
          namesHtml.append(String.format("<div class=\"stat-row\"><span class=\"stat-label\">Rōmaji</span><span>%s</span></div>", 
              data.get("romajiName")));
      }
      result = result.replace("${namesSection}", namesHtml.toString());
  
      // Handle evolution chain
      @SuppressWarnings("unchecked")
      Map<String, String> prevPokemon = (Map<String, String>) data.get("prevPokemon");
      String prevHtml = "";
      if (prevPokemon != null) {
          prevHtml = String.format(
              "<a href=\"/resource/%s\" class=\"pokemon-card\">" +
              "<h3>%s</h3>" +
              "<p>#%s</p>" +
              "</a>" +
              "<span class=\"evolution-arrow\">→</span>",
              prevPokemon.get("id"), prevPokemon.get("name"), prevPokemon.get("id"));
      }
      result = result.replace("${prevPokemonSection}", prevHtml);
  
      @SuppressWarnings("unchecked")
      Map<String, String> nextPokemon = (Map<String, String>) data.get("nextPokemon");
      String nextHtml = "";
      if (nextPokemon != null) {
          nextHtml = String.format(
              "<span class=\"evolution-arrow\">→</span>" +
              "<a href=\"/resource/%s\" class=\"pokemon-card\">" +
              "<h3>%s</h3>" +
              "<p>#%s</p>" +
              "</a>",
              nextPokemon.get("id"), nextPokemon.get("name"), nextPokemon.get("id"));
      }
      result = result.replace("${nextPokemonSection}", nextHtml);
  
      // Handle external links
      StringBuilder linksHtml = new StringBuilder();
      linksHtml.append(String.format(
          "<a href=\"https://bulbapedia.bulbagarden.net/wiki/%s_(Pok%%C3%%A9mon)\" " +
          "class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">Bulbapedia</a>",
          name));
  
      if (data.containsKey("dbpediaLink")) {
          linksHtml.append(String.format(
              "<a href=\"%s\" class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">DBpedia</a>",
              data.get("dbpediaLink")));
      }
      if (data.containsKey("wikidataLink")) {
          linksHtml.append(String.format(
              "<a href=\"%s\" class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">Wikidata</a>",
              data.get("wikidataLink")));
      }
      result = result.replace("${externalLinksSection}", linksHtml.toString());

      return result;
  }

    public void stop() {
        Spark.stop();
    }
}
