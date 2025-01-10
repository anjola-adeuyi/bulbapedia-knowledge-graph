package org.example.server;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SPARQLHandler {
    private static final Logger logger = LoggerFactory.getLogger(SPARQLHandler.class);
    private final Dataset dataset;

    public SPARQLHandler(Dataset dataset) {
        this.dataset = dataset;
    }

    public ResultSet executeQuery(String queryString) {
        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
        return qexec.execSelect();
    }

    public void executeTestQueries() {
        // Test query 1: List all Pokemon
        String query1 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                       "PREFIX pokemon: <http://example.org/pokemon/>\n" +
                       "PREFIX schema: <http://schema.org/>\n" +
                       "SELECT ?pokemon ?name WHERE {\n" +
                       "  ?pokemon rdf:type pokemon:Pokemon ;\n" +
                       "           schema:name ?name .\n" +
                       "}";

        // Test query 2: Find Pokemon by type
        String query2 = "PREFIX pokemon: <http://example.org/pokemon/>\n" +
                       "PREFIX schema: <http://schema.org/>\n" +
                       "SELECT ?name ?type WHERE {\n" +
                       "  ?pokemon schema:name ?name ;\n" +
                       "           pokemon:primaryType ?type .\n" +
                       "}";

        logger.info("Executing test queries:");
        
        logger.info("Query 1 - List all Pokemon:");
        ResultSet results1 = executeQuery(query1);
        while (results1.hasNext()) {
            QuerySolution solution = results1.next();
            logger.info("Pokemon: " + solution.get("name"));
        }

        logger.info("Query 2 - Pokemon types:");
        ResultSet results2 = executeQuery(query2);
        while (results2.hasNext()) {
            QuerySolution solution = results2.next();
            logger.info(solution.get("name") + " - Type: " + solution.get("type"));
        }
    }
}
