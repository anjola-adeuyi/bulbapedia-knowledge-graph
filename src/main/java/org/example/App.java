package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.parser.WikiInfoboxParser;
import org.example.rdf.PokemonRDFConverter;
import org.example.server.EndpointTester;
import org.example.server.PokemonFusekiServer;
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
            
            // Get first Pokemon data
            logger.info("Fetching Bulbasaur data...");
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
            
            // Execute test queries
            logger.info("\nExample SPARQL queries to try in the SPARQL interface (http://localhost:3330/dataset/query):");
            logger.info("\n1. List all Pokemon:");
            logger.info("PREFIX schema: <http://schema.org/>\n" +
                       "SELECT ?name WHERE { ?s schema:name ?name }");
            
            logger.info("\n2. Get Pokemon details:");
            logger.info("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                       "PREFIX pokemon: <http://example.org/pokemon/>\n" +
                       "PREFIX schema: <http://schema.org/>\n" +
                       "SELECT ?name ?type1 ?type2 ?height ?weight WHERE {\n" +
                       "  ?pokemon rdf:type pokemon:Pokemon ;\n" +
                       "           schema:name ?name ;\n" +
                       "           pokemon:primaryType ?type1 ;\n" +
                       "           schema:height ?height ;\n" +
                       "           schema:weight ?weight .\n" +
                       "  OPTIONAL { ?pokemon pokemon:secondaryType ?type2 }\n" +
                       "}");
            
            // Test the endpoints
            EndpointTester tester = new EndpointTester("http://localhost:" + 3330 + "/pokemon");
            tester.testEndpoints();

            // Keep the server running
            logger.info("\nServer is running. Visit http://localhost:3330/query.html to try SPARQL queries");
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
