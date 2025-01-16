package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.client.DataCollectionCoordinator;
import org.example.linking.ExternalLinker;
import org.example.parser.MultilingualDataHandler;
import org.example.rdf.PokemonRDFConverter;
import org.example.server.PokemonFusekiServer;
import org.example.server.LinkedDataServer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.List;
import java.util.Map;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        PokemonFusekiServer fusekiServer = null;
        
        try {
            // Initialize components
            BulbapediaClient client = new BulbapediaClient();
            PokemonRDFConverter converter = new PokemonRDFConverter();
            
            // Create a combined model for all Pokemon
            Model combinedModel = ModelFactory.createDefaultModel();

            DataCollectionCoordinator coordinator = new DataCollectionCoordinator();
            List<Map<String, String>> allPokemonData = coordinator.collectAllData();

            // Process the collected data
            for (Map<String, String> pokemonData : allPokemonData) {
                try {
                    Model pokemonModel = converter.convertToRDF(pokemonData);
                    combinedModel.add(pokemonModel);
                } catch (Exception e) {
                    logger.error("Error processing Pokemon data: {}", pokemonData.get("title"), e);
                }
            }

            // Add multilingual labels
            MultilingualDataHandler multiHandler = new MultilingualDataHandler();
            multiHandler.loadTSVData();
            multiHandler.enrichModelWithLabels(combinedModel);

            // Add external links
            logger.info("Starting external linking process");
            ExternalLinker linker = new ExternalLinker();
            linker.addExternalLinks(combinedModel);

            // Save the combined model
            String outputFile = "pokemon.ttl";

            // Before saving, try to load existing data if any
            try {
                Model existingModel = ModelFactory.createDefaultModel();
                java.io.File file = new java.io.File(outputFile);
                if (file.exists() && file.length() > 0) {
                    existingModel.read(new java.io.FileInputStream(file), null, "TURTLE");
                    combinedModel.add(existingModel);
                }
            } catch (Exception e) {
                logger.warn("Could not load existing model, creating new one", e);
            }

            // Now save the combined model
            converter.saveModel(combinedModel, outputFile);
            logger.info("RDF data saved to " + outputFile);

            // Start Fuseki server and load data
            fusekiServer = new PokemonFusekiServer();
            fusekiServer.start();
            fusekiServer.loadData(combinedModel);

            // Start Linked Data interface
            LinkedDataServer ldServer = new LinkedDataServer(fusekiServer.getDataset(), 3331);
            ldServer.start();

            // Keep the server running
            logger.info("\nServer is running. Press Enter to stop...");
            System.in.read();

        } catch (Exception e) {
            logger.error("Error occurred:", e);
        } finally {
            if (fusekiServer != null) {
                fusekiServer.stop();
            }
        }
    }
}
