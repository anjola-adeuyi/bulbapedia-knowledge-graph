package org.example.server;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
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
        logger.info("2. SPARQL Query endpoint: http://localhost:" + PORT + "/" + DATASET_NAME + "/sparql");
        logger.info("3. Try query examples with curl:");
        logger.info("   curl -X POST -H 'Content-Type: application/sparql-query' --data 'PREFIX schema: <http://schema.org/> SELECT ?name WHERE { ?s schema:name ?name }' http://localhost:" + PORT + "/" + DATASET_NAME + "/query");
    }

    public void stop() {
        server.stop();
        logger.info("Fuseki server stopped");
    }

    public void loadData(Model model) {
        dataset.getDefaultModel().removeAll();
        dataset.getDefaultModel().add(model);
        logger.info("Loaded {} triples into the default graph", model.size());
    }

    public Dataset getDataset() {
        return dataset;
    }
}
