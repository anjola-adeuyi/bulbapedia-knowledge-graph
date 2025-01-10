package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.parser.WikiInfoboxParser;
import org.example.rdf.PokemonRDFConverter;
import org.example.server.PokemonFusekiServer;
import org.example.server.SPARQLHandler;
import org.apache.jena.rdf.model.Model;
import org.json.JSONObject;
import java.util.Map;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        PokemonFusekiServer fusekiServer = null;
        
        try {
            // Initialize components
            BulbapediaClient client = new BulbapediaClient();
            WikiInfoboxParser parser = new WikiInfoboxParser();
            PokemonRDFConverter converter = new PokemonRDFConverter();
            
            // Get Bulbasaur data
            JSONObject response = client.getPageContent("Bulbasaur_(Pokémon)");
            Map<String, String> pokemonInfo = parser.extractPokemonInfo(response);
            
            // Convert to RDF
            Model rdfModel = converter.convertToRDF(pokemonInfo);
            
            // Save to file
            String outputFile = "pokemon.ttl";
            converter.saveToFile(outputFile);
            logger.info("RDF data saved to " + outputFile);
            
            // Start Fuseki server
            fusekiServer = new PokemonFusekiServer();
            fusekiServer.start();
            
            // Load data into Fuseki
            fusekiServer.loadData(rdfModel);
            
            // Execute test SPARQL queries
            SPARQLHandler sparqlHandler = new SPARQLHandler(fusekiServer.getDataset());
            sparqlHandler.executeTestQueries();
            
            // Keep the server running
            logger.info("Press Enter to stop the server...");
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
