package org.example.server;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.SPARQL_QueryGeneral;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PokemonFusekiServer {
    private static final Logger logger = LoggerFactory.getLogger(PokemonFusekiServer.class);
    private static final String DATASET_PATH = "/dataset";
    private static final int PORT = 3330;
    
    private final FusekiServer server;
    private final Dataset dataset;

    public PokemonFusekiServer() {
        // Create an in-memory dataset
        dataset = DatasetFactory.createTxnMem();
        
        // Configure and create the server with all endpoints enabled
        server = FusekiServer.create()
                .port(PORT)
                .add(DATASET_PATH, dataset, true)  // true enables SPARQL endpoints
                .enableCors(true)
                .build();
    }

    public void start() {
        server.start();
        logger.info("Fuseki server started on port " + PORT);
        logger.info("Access the following endpoints:");
        logger.info("1. Main endpoint: http://localhost:" + PORT + DATASET_PATH);
        logger.info("2. SPARQL Query interface: http://localhost:" + PORT + DATASET_PATH + "/query");
        logger.info("3. SPARQL endpoint: http://localhost:" + PORT + DATASET_PATH + "/sparql");
        logger.info("4. SPARQL Update endpoint: http://localhost:" + PORT + DATASET_PATH + "/update");
    }

    public void stop() {
        server.stop();
        logger.info("Fuseki server stopped");
    }

    public void loadData(Model model) {
        dataset.getDefaultModel().removeAll();  // Clear existing data
        dataset.getDefaultModel().add(model);   // Add new data
        logger.info("Loaded {} triples into the default graph", model.size());
    }

    public Dataset getDataset() {
        return dataset;
    }
}
