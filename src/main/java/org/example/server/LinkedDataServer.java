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
import java.io.StringWriter;

public class LinkedDataServer {
    private static final Logger logger = LoggerFactory.getLogger(LinkedDataServer.class);
    private final Dataset dataset;
    private final int port;

    public LinkedDataServer(Dataset dataset, int port) {
        this.dataset = dataset;
        this.port = port;
    }

    public void start() {
        Spark.port(port);
        Spark.get("/resource/:id", this::handleResourceRequest);

        Spark.before((request, response) -> {
            String accept = request.headers("Accept");
            if (accept != null && accept.contains("text/html")) {
                response.type("text/html");
            } else {
                response.type("text/turtle");
            }
        });

        logger.info("Linked Data interface started on port {}", port);
    }

    private Object handleResourceRequest(Request request, Response response) {
        String id = request.params(":id");
        String resourceUri = "http://example.org/pokemon/pokemon/" + id;

        String constructQuery = String.format(
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX schema: <http://schema.org/>\n" +
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "CONSTRUCT {\n" +
            "  <%s> ?p ?o .\n" +
            "  ?s pokemon:evolvesFrom <%s> .\n" +
            "  <%s> pokemon:evolvesFrom ?prev .\n" +
            "} WHERE {\n" +
            "  { <%s> ?p ?o }\n" +
            "  UNION\n" +
            "  { ?s pokemon:evolvesFrom <%s> }\n" +
            "  UNION\n" +
            "  { <%s> pokemon:evolvesFrom ?prev }\n" +
            "}", resourceUri, resourceUri, resourceUri, resourceUri, resourceUri, resourceUri);

        try (QueryExecution qexec = QueryExecutionFactory.create(constructQuery, dataset)) {
            Model description = qexec.execConstruct();

            if (description.isEmpty()) {
                response.status(404);
                return "Resource not found";
            }

            if (response.type().equals("text/html")) {
                return createHtmlView(description, id);
            } else {
                StringWriter writer = new StringWriter();
                RDFDataMgr.write(writer, description, Lang.TURTLE);
                return writer.toString();
            }
        }
    }

    private String createHtmlView(Model model, String id) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>")
            .append("<title>Pokémon #").append(id).append("</title>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }")
            .append("h1 { color: #2c3e50; margin-bottom: 20px; }")
            .append(".pokemon-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }")
            .append(".info-section { margin-bottom: 20px; }")
            .append(".info-section h2 { color: #3498db; font-size: 1.2em; margin-bottom: 10px; }")
            .append("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }")
            .append("th, td { border: 1px solid #e1e1e1; padding: 12px; text-align: left; }")
            .append("th { background-color: #f8f9fa; color: #2c3e50; }")
            .append("tr:nth-child(even) { background-color: #f8f9fa; }")
            .append(".type-badge { display: inline-block; padding: 4px 8px; border-radius: 4px; color: white; margin-right: 5px; }")
            .append(".type-Grass { background-color: #78c850; }")
            .append(".type-Poison { background-color: #a040a0; }")
            .append(".evolution-chain { display: flex; align-items: center; justify-content: center; margin: 20px 0; background-color: #f8f9fa; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }")
            .append(".evolution-arrow { margin: 0 15px; color: #666; font-size: 20px; }")
            .append(".pokemon-link { text-decoration: none; color: #2c3e50; padding: 8px 16px; border-radius: 4px; background: #e9ecef; transition: all 0.2s ease; font-weight: 500; }")
            .append(".pokemon-link.current { background: #3498db; color: white; }")
            .append(".pokemon-link:not(.current):hover { background: #dee2e6; transform: translateY(-1px); }")
            .append("</style></head><body>");

        // Execute SPARQL query to get Pokemon details
        String detailsQuery = String.format(
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX schema: <http://schema.org/>\n" +
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "SELECT ?name ?primaryType ?secondaryType ?category ?height ?weight ?japaneseName ?romajiName " +
            "WHERE { " +
            "  <http://example.org/pokemon/pokemon/%s> " +
            "    schema:name ?name ; " +
            "    pokemon:primaryType ?primaryType ; " +
            "    pokemon:category ?category ; " +
            "    schema:height ?height ; " +
            "    schema:weight ?weight ; " +
            "    pokemon:japaneseName ?japaneseName ; " +
            "    pokemon:romajiName ?romajiName . " +
            "  OPTIONAL { <http://example.org/pokemon/pokemon/%s> pokemon:secondaryType ?secondaryType } " +
            "}", id, id);

        try (QueryExecution qexec = QueryExecutionFactory.create(detailsQuery, model)) {
            ResultSet results = qexec.execSelect();
            
            if (results.hasNext()) {
                QuerySolution soln = results.next();
                addPokemonDetails(html, soln, id);
            }

            addEvolutionChain(html, model, id);

            html.append("</div></body></html>");
            return html.toString();
        }
    }

    private void addPokemonDetails(StringBuilder html, QuerySolution soln, String id) {
        String name = soln.getLiteral("name").getString();
        String primaryType = soln.getLiteral("primaryType").getString();
        String category = soln.getLiteral("category").getString();
        
        html.append("<div class='pokemon-card'>")
            .append("<h1>").append(name).append(" #").append(id).append("</h1>")
            .append("<div class='info-section'>")
            .append("<h2>Types</h2>")
            .append("<span class='type-badge type-").append(primaryType).append("'>").append(primaryType).append("</span>");
        
        if (soln.contains("secondaryType")) {
            String secondaryType = soln.getLiteral("secondaryType").getString();
            html.append("<span class='type-badge type-").append(secondaryType).append("'>").append(secondaryType).append("</span>");
        }
        
        html.append("</div><div class='info-section'>")
            .append("<h2>Details</h2>")
            .append("<table>")
            .append("<tr><th>Category</th><td>").append(category).append(" Pokémon</td></tr>")
            .append("<tr><th>Height</th><td>").append(soln.getLiteral("height").getString()).append(" m</td></tr>")
            .append("<tr><th>Weight</th><td>").append(soln.getLiteral("weight").getString()).append(" kg</td></tr>")
            .append("</table></div>")
            .append("<div class='info-section'>")
            .append("<h2>Names</h2>")
            .append("<table>")
            .append("<tr><th>English</th><td>").append(name).append("</td></tr>")
            .append("<tr><th>Japanese</th><td>").append(soln.getLiteral("japaneseName").getString()).append("</td></tr>")
            .append("<tr><th>Rōmaji</th><td>").append(soln.getLiteral("romajiName").getString()).append("</td></tr>")
            .append("</table></div>");
    }

    private void addEvolutionChain(StringBuilder html, Model model, String id) {
        String evolutionQuery = String.format(
            "PREFIX schema: <http://schema.org/>\n" +
            "PREFIX pokemon: <http://example.org/pokemon/>\n" +
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "SELECT DISTINCT ?current ?currentName ?prev ?prevName ?next ?nextName\n" +
            "WHERE {\n" +
            "  BIND(<http://example.org/pokemon/pokemon/%s> as ?current)\n" +
            "  ?current schema:name ?currentName .\n" +
            "  OPTIONAL {\n" +
            "    ?current pokemon:evolvesFrom ?prev .\n" +
            "    ?prev schema:name ?prevName .\n" +
            "  }\n" +
            "  OPTIONAL {\n" +
            "    ?next pokemon:evolvesFrom ?current .\n" +
            "    ?next schema:name ?nextName .\n" +
            "  }\n" +
            "}", id);

        html.append("<div class='info-section'>")
            .append("<h2>Evolution Chain</h2>")
            .append("<div class='evolution-chain'>");

        try (QueryExecution qexec = QueryExecutionFactory.create(evolutionQuery, model)) {
            ResultSet results = qexec.execSelect();
            
            if (results.hasNext()) {
                QuerySolution soln = results.next();
                String currentName = soln.getLiteral("currentName").getString();
                
                // Previous evolution
                if (soln.contains("prevName")) {
                    String prevName = soln.getLiteral("prevName").getString();
                    String prevId = extractId(soln.getResource("prev").getURI());
                    html.append("<a href='/resource/").append(prevId)
                        .append("' class='pokemon-link'>")
                        .append(prevName)
                        .append(" (#").append(prevId).append(")</a>")
                        .append("<span class='evolution-arrow'>→</span>");
                }
                
                // Current Pokemon
                html.append("<span class='pokemon-link current'>")
                    .append(currentName)
                    .append(" (#").append(id).append(")</span>");
                
                // Next evolution
                if (soln.contains("nextName")) {
                    String nextName = soln.getLiteral("nextName").getString();
                    String nextId = extractId(soln.getResource("next").getURI());
                    html.append("<span class='evolution-arrow'>→</span>")
                        .append("<a href='/resource/").append(nextId)
                        .append("' class='pokemon-link'>")
                        .append(nextName)
                        .append(" (#").append(nextId).append(")</a>");
                }
            }
            
            html.append("</div></div>");
        }
    }

    private String extractId(String uri) {
        return uri.substring(uri.lastIndexOf("/") + 1);
    }

    public void stop() {
        Spark.stop();
    }
}
