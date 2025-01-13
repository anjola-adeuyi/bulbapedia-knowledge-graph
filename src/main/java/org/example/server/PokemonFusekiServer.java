package org.example.server;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.example.inference.InferenceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PokemonFusekiServer {
    private static final Logger logger = LoggerFactory.getLogger(PokemonFusekiServer.class);
    private static final String DATASET_NAME = "pokemon";
    private static final int PORT = 3330;
    
    private final FusekiServer server;
    private final Dataset dataset;

    public PokemonFusekiServer() {
        // Create an in-memory dataset
        dataset = DatasetFactory.createTxnMem();
        
        // Configure and create the server
        server = FusekiServer.create()
                .port(PORT)
                .staticFileBase("webapp")
                .enableCors(true)
                .add("/" + DATASET_NAME, dataset)
                .addEndpoint("/" + DATASET_NAME, "/query", Operation.Query)
                .addEndpoint("/" + DATASET_NAME, "/sparql", Operation.Query)
                .addEndpoint("/" + DATASET_NAME, "/update", Operation.Update)
                .build();
    }

    public void start() {
        server.start();
        logger.info("Fuseki server started on port " + PORT);
        logger.info("Access the following endpoints:");
        logger.info("1. Main endpoint: http://localhost:" + PORT + "/" + DATASET_NAME);
        logger.info("2. SPARQL Query endpoint: http://localhost:" + PORT + "/" + DATASET_NAME + "/query");
        logger.info("\nExample queries:");
        logger.info("1. Using curl:");
        logger.info("curl -X POST -H 'Content-Type: application/sparql-query' \\\n" +
                   "--data 'PREFIX schema: <http://schema.org/> \\\n" +
                   "SELECT ?name ?type1 ?type2 WHERE { \\\n" +
                   "  ?s schema:name ?name ; \\\n" +
                   "     pokemon:primaryType ?type1 . \\\n" +
                   "  OPTIONAL { ?s pokemon:secondaryType ?type2 } \\\n" +
                   "}' \\\n" +
                   "http://localhost:" + PORT + "/" + DATASET_NAME + "/query");
        
        logger.info("\n2. Using Postman:");
        logger.info("URL: http://localhost:" + PORT + "/" + DATASET_NAME + "/query");
        logger.info("Method: POST");
        logger.info("Header: Content-Type: application/sparql-query");
        logger.info("Body: Your SPARQL query");
    }

    public void stop() {
        server.stop();
        logger.info("Fuseki server stopped");
    }

    public void loadData(Model model) {
        // Add inference rules to the model
        Model inferenceModel = InferenceHandler.addInferenceRules(model);
        
        // Clear existing data
        dataset.getDefaultModel().removeAll();
        
        // Add the inference model
        dataset.getDefaultModel().add(inferenceModel);
        
        logger.info("Loaded {} triples into the default graph", inferenceModel.size());
        logger.info("Model includes inferred statements from:");
        logger.info("- owl:sameAs relationships");
        logger.info("- RDFS subclass hierarchy");
        logger.info("- Property inheritance");
        
        // Log sample query
        logger.info("\nData loaded successfully. Try this query to test inference:");
        logger.info("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>");
        logger.info("PREFIX owl: <http://www.w3.org/2002/07/owl#>");
        logger.info("PREFIX pokemon: <http://example.org/pokemon/>");
        logger.info("SELECT ?pokemon ?name ?type WHERE {");
        logger.info("  ?pokemon rdfs:subClassOf* pokemon:Pokemon ;");
        logger.info("           owl:sameAs* ?equiv .");
        logger.info("  { ?pokemon schema:name ?name } UNION");
        logger.info("  { ?equiv schema:name ?name }");
        logger.info("  ?pokemon pokemon:primaryType ?type");
        logger.info("} LIMIT 5");
    }

    public Dataset getDataset() {
        return dataset;
    }
}
