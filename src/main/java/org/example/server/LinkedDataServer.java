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
        String result = htmlTemplate;

        // Handle primary type badge
        String type = (String) data.get("primaryType");
        String typeBadge = String.format(
            "<span class=\"type-badge\" style=\"background-color: var(--%s-color)\">%s</span>",
            type.toLowerCase(), type
        );

        // Handle secondary type badge
        if (data.containsKey("secondaryType")) {
            String secondaryType = (String) data.get("secondaryType");
            typeBadge += String.format(
                "<span class=\"type-badge\" style=\"background-color: var(--%s-color)\">%s</span>",
                secondaryType.toLowerCase(), secondaryType
            );
        }
        result = result.replace("${typeBadges}", typeBadge);

        // Handle basic properties
        result = result.replace("${name}", (String) data.get("name"));
        result = result.replace("${id}", (String) data.get("id"));
        result = result.replace("${height}", data.get("height").toString());
        result = result.replace("${weight}", data.get("weight").toString());
        result = result.replace("${category}", (String) data.get("category"));

        // Handle names section
        String englishName = (String) data.get("name");
        String japaneseName = (String) data.getOrDefault("japaneseName", "");
        String romajiName = (String) data.getOrDefault("romajiName", "");

        StringBuilder namesHtml = new StringBuilder();
        namesHtml.append("<div class=\"stat-row\"><span class=\"stat-label\">English</span><span>")
                .append(englishName)
                .append("</span></div>");

        if (!japaneseName.isEmpty()) {
            namesHtml.append("<div class=\"stat-row\"><span class=\"stat-label\">Japanese</span><span>")
                    .append(japaneseName)
                    .append("</span></div>");
        }

        if (!romajiName.isEmpty()) {
            namesHtml.append("<div class=\"stat-row\"><span class=\"stat-label\">Rōmaji</span><span>")
                    .append(romajiName)
                    .append("</span></div>");
        }
        result = result.replace("${namesSection}", namesHtml.toString());

        // Handle evolution chain
        String prevPokemonHtml = "";
        @SuppressWarnings("unchecked")
        Map<String, String> prevPokemon = (Map<String, String>) data.get("prevPokemon");
        if (prevPokemon != null) {
            prevPokemonHtml = String.format(
                "<a href=\"/resource/%s\" class=\"pokemon-card\">" +
                "<h3>%s</h3>" +
                "<p>#%s</p>" +
                "</a>" +
                "<span class=\"evolution-arrow\">→</span>",
                prevPokemon.get("id"),
                prevPokemon.get("name"),
                prevPokemon.get("id")
            );
        }

        String nextPokemonHtml = "";
        @SuppressWarnings("unchecked")
        Map<String, String> nextPokemon = (Map<String, String>) data.get("nextPokemon");
        if (nextPokemon != null) {
            nextPokemonHtml = String.format(
                "<span class=\"evolution-arrow\">→</span>" +
                "<a href=\"/resource/%s\" class=\"pokemon-card\">" +
                "<h3>%s</h3>" +
                "<p>#%s</p>" +
                "</a>",
                nextPokemon.get("id"),
                nextPokemon.get("name"),
                nextPokemon.get("id")
            );
        }

        // Replace evolution chain placeholders
        result = result.replace("${prevPokemonSection}", prevPokemonHtml);
        result = result.replace("${nextPokemonSection}", nextPokemonHtml);

        // Handle external links
        StringBuilder externalLinksHtml = new StringBuilder();
        externalLinksHtml.append(String.format(
            "<a href=\"https://bulbapedia.bulbagarden.net/wiki/%s_(Pok%%C3%%A9mon)\" " +
            "class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">Bulbapedia</a>",
            englishName
        ));

        if (data.containsKey("dbpediaLink")) {
            externalLinksHtml.append(String.format(
                "<a href=\"%s\" class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">DBpedia</a>",
                data.get("dbpediaLink")
            ));
        }

        if (data.containsKey("wikidataLink")) {
            externalLinksHtml.append(String.format(
                "<a href=\"%s\" class=\"external-link\" target=\"_blank\" rel=\"noopener noreferrer\">Wikidata</a>",
                data.get("wikidataLink")
            ));
        }

        result = result.replace("${externalLinksSection}", externalLinksHtml.toString());

        // Clean up any remaining conditional statements
        result = result.replaceAll("\\$\\{\\w+\\s*!=\\s*null\\s*\\?\\s*'[^']*'\\s*:\\s*''\\}", "");
        result = result.replaceAll("\\$\\{[^}]+\\}", ""); // Clean up any remaining variables

        return result;
    }

    public void stop() {
        Spark.stop();
    }
}
