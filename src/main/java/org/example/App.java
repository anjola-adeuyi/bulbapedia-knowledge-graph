package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.client.EvolutionChainFetcher;
import org.example.parser.WikiInfoboxParser;
import org.example.rdf.PokemonRDFConverter;
import org.example.server.PokemonFusekiServer;
import org.example.validation.PokemonShapes;
import org.example.validation.RDFValidator;
import org.example.server.EndpointTester;
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
            WikiInfoboxParser parser = new WikiInfoboxParser();
            PokemonRDFConverter converter = new PokemonRDFConverter();
            EvolutionChainFetcher fetcher = new EvolutionChainFetcher(client);
            
            // Create a combined model for all Pokemon
            Model combinedModel = ModelFactory.createDefaultModel();
            
            // Fetch the entire evolution chain
            logger.info("Fetching Bulbasaur evolution chain data...");
            List<Map<String, String>> evolutionChainData = fetcher.fetchEvolutionChain();
            
            // Process each Pokemon in the chain
            for (Map<String, String> pokemonData : evolutionChainData) {
                Map<String, String> pokemonInfo = parser.processWikitext(pokemonData);
                Model pokemonModel = converter.convertToRDF(pokemonInfo);
                combinedModel.add(pokemonModel);
            }
            
            // Save the combined model
            String outputFile = "pokemon.ttl";
            converter.saveModel(combinedModel, outputFile);
            logger.info("RDF data saved to " + outputFile);
            
            // Create and save SHACL shapes
            PokemonShapes shapes = new PokemonShapes();
            shapes.saveShapes("pokemon-shapes.ttl");
            
            // Validate the RDF data
            RDFValidator validator = new RDFValidator(shapes.createShapes());
            boolean isValid = validator.validate(combinedModel);
            
            if (isValid) {
                // Start Fuseki server
            fusekiServer = new PokemonFusekiServer();
            fusekiServer.start();
            
            // Load data into Fuseki
            fusekiServer.loadData(combinedModel);
            
            // Start Linked Data interface
            LinkedDataServer ldServer = new LinkedDataServer(fusekiServer.getDataset(), 3331);
            ldServer.start();
            
            // Test the endpoints
            EndpointTester tester = new EndpointTester("http://localhost:3330/pokemon");
            tester.testEndpoints();
            
            // Print example complex queries
            logger.info("\nExample complex SPARQL queries to try:");
            logger.info("\n1. Find all Pokemon in the evolution chain:");
            logger.info("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                       "PREFIX pokemon: <http://example.org/pokemon/>\n" +
                       "PREFIX schema: <http://schema.org/>\n" +
                       "SELECT ?name ?stage ?evolves_from WHERE {\n" +
                       "  ?pokemon rdf:type pokemon:Pokemon ;\n" +
                       "           schema:name ?name ;\n" +
                       "           pokemon:evolutionStage ?stage .\n" +
                       "  OPTIONAL { ?pokemon pokemon:evolvesFrom ?evolves_from }\n" +
                       "} ORDER BY ?stage");
            
            // Keep the server running
            logger.info("\nServer is running. Use POST requests to /pokemon/query endpoint");
            logger.info("Press Enter to stop the server...");
            System.in.read();
            
        } else {
            logger.error("RDF data is not valid. Please check the data and shapes.");
        }
            }
        catch (Exception e) {
            logger.error("Error occurred:", e);
        } finally {
            if (fusekiServer != null) {
                fusekiServer.stop();
            }
        }
    }
}
