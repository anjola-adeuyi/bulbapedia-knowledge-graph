package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.parser.WikiInfoboxParser;
import org.example.rdf.PokemonRDFConverter;
import org.apache.jena.rdf.model.Model;
import org.json.JSONObject;
import java.util.Map;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        
        try {
            BulbapediaClient client = new BulbapediaClient();
            WikiInfoboxParser parser = new WikiInfoboxParser();
            PokemonRDFConverter converter = new PokemonRDFConverter();
            
            // Get data for Bulbasaur as a test
            JSONObject response = client.getPageContent("Bulbasaur_(Pokémon)");
            Map<String, String> pokemonInfo = parser.extractPokemonInfo(response);
            
            // Print extracted information
            logger.info("Extracted Pokemon Information:");
            pokemonInfo.forEach((key, value) -> {
                logger.info(key + ": " + value);
            });

            // Convert to RDF
            Model rdfModel = converter.convertToRDF(pokemonInfo);
            
            // Save to file
            String outputFile = "pokemon.ttl";
            converter.saveToFile(outputFile);
            logger.info("RDF data saved to " + outputFile);
            
            // Print the model to console in Turtle format
            logger.info("Generated RDF (Turtle format):");
            rdfModel.write(System.out, "TURTLE");
            
        } catch (Exception e) {
            logger.error("Error occurred:", e);
        }
    }
}
