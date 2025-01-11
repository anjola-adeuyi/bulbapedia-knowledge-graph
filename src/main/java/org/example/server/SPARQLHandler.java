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
        // Test query 1: List all Pokemon with their properties
        String query1 = createPrefixes() +
                       "SELECT DISTINCT ?name ?type1 ?type2 ?height ?weight WHERE {\n" +
                       "  ?pokemon rdf:type pokemon:Pokemon ;\n" +
                       "           schema:name ?name ;\n" +
                       "           pokemon:primaryType ?type1 ;\n" +
                       "           schema:height ?height ;\n" +
                       "           schema:weight ?weight .\n" +
                       "  OPTIONAL { ?pokemon pokemon:secondaryType ?type2 }\n" +
                       "} ORDER BY ?name";

        // Test query 2: Find Pokemon abilities
        String query2 = createPrefixes() +
                       "SELECT ?name ?ability WHERE {\n" +
                       "  ?pokemon schema:name ?name ;\n" +
                       "           pokemon:primaryAbility ?abilityResource .\n" +
                       "  ?abilityResource rdfs:label ?ability .\n" +
                       "}";

        logger.info("Executing test queries:");
        
        logger.info("Query 1 - Pokemon details:");
        executePokemonDetailsQuery(query1);

        logger.info("\nQuery 2 - Pokemon abilities:");
        executePokemonAbilitiesQuery(query2);

        printExampleQueries();
    }

    private void executePokemonDetailsQuery(String query) {
        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution solution = results.next();
                StringBuilder sb = new StringBuilder();
                sb.append("Name: ").append(solution.get("name")).append(" | ");
                sb.append("Type1: ").append(solution.get("type1"));
                if (solution.contains("type2")) {
                    sb.append("/").append(solution.get("type2"));
                }
                sb.append(" | Height: ").append(solution.get("height")).append("m");
                sb.append(" | Weight: ").append(solution.get("weight")).append("kg");
                logger.info(sb.toString());
            }
        }
    }

    private void executePokemonAbilitiesQuery(String query) {
        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution solution = results.next();
                logger.info(solution.get("name") + " - Ability: " + solution.get("ability"));
            }
        }
    }

    private void printExampleQueries() {
        logger.info("\nExample SPARQL queries for testing in the web interface:");
        logger.info("1. List all Pokemon:\n" + 
                   createPrefixes() +
                   "SELECT ?name WHERE { ?s schema:name ?name }");
        
        logger.info("\n2. Find Pokemon by type:\n" +
                   createPrefixes() +
                   "SELECT ?name WHERE { ?s schema:name ?name ; pokemon:primaryType \"Grass\" }");
    }

    private String createPrefixes() {
        return "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
               "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
               "PREFIX pokemon: <http://example.org/pokemon/>\n" +
               "PREFIX schema: <http://schema.org/>\n";
    }
}
