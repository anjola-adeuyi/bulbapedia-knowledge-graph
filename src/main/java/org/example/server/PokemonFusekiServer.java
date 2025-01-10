package org.example.server;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetImpl;
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
                .add("/" + DATASET_NAME, dataset, true)  // true enables SPARQL endpoint
                .enableCors(true)                        // Enable CORS for browser access
                .build();
    }

    public void start() {
        server.start();
        logger.info("Fuseki server started on port " + PORT);
        logger.info("SPARQL endpoint available at http://localhost:" + PORT + "/" + DATASET_NAME);
        logger.info("SPARQL query interface available at http://localhost:" + PORT + "/" + DATASET_NAME + "/query");
        logger.info("SPARQL update interface available at http://localhost:" + PORT + "/" + DATASET_NAME + "/update");
    }

    public void stop() {
        server.stop();
        logger.info("Fuseki server stopped");
    }

    public void loadData(Model model) {
        dataset.setDefaultModel(model);
        logger.info("Loaded {} triples into the default graph", model.size());
    }

    public Dataset getDataset() {
        return dataset;
    }
}
