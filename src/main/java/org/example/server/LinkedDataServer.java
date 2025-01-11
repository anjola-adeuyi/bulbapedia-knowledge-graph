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

        // Handle resource requests
        Spark.get("/resource/:id", this::handleResourceRequest);

        // Handle content negotiation
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

        // Query to get resource description
        String queryStr = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }" +
                         "VALUES ?s { <" + resourceUri + "> }";

        try (QueryExecution qexec = QueryExecutionFactory.create(queryStr, dataset)) {
            Model description = qexec.execConstruct();

            if (description.isEmpty()) {
                response.status(404);
                return "Resource not found";
            }

            // Return HTML or Turtle based on Accept header
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
        html.append("<!DOCTYPE html><html><head><title>Pokemon: ").append(id).append("</title>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 20px; }")
            .append("h1 { color: #333; }")
            .append("table { border-collapse: collapse; width: 100%; }")
            .append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
            .append("th { background-color: #f2f2f2; }")
            .append("</style></head><body>")
            .append("<h1>Pokemon: ").append(id).append("</h1>");

        // Execute SPARQL query to get properties in a table
        String queryStr = "SELECT ?p ?o WHERE { <http://example.org/pokemon/pokemon/" + id + "> ?p ?o }";
        try (QueryExecution qexec = QueryExecutionFactory.create(queryStr, model)) {
            ResultSet results = qexec.execSelect();

            html.append("<table><tr><th>Property</th><th>Value</th></tr>");
            while (results.hasNext()) {
                QuerySolution soln = results.next();
                String prop = soln.get("p").toString().replaceAll("^.*[/#]", "");
                String value = soln.get("o").toString().replaceAll("^.*[/#]", "");
                html.append("<tr><td>").append(prop).append("</td><td>").append(value).append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    public void stop() {
        Spark.stop();
    }
}
