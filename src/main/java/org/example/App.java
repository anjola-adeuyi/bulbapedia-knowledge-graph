package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.example.parser.WikiInfoboxParser;
import org.json.JSONObject;
import java.util.Map;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        
        try {
            BulbapediaClient client = new BulbapediaClient();
            WikiInfoboxParser parser = new WikiInfoboxParser();
            
            // Get data for Bulbasaur as a test
            JSONObject response = client.getPageContent("Bulbasaur");
            Map<String, String> pokemonInfo = parser.extractPokemonInfo(response);
            
            // Print extracted information
            logger.info("Extracted Pokemon Information:");
            pokemonInfo.forEach((key, value) -> {
                logger.info(key + ": " + value);
            });
            
        } catch (Exception e) {
            logger.error("Error occurred:", e);
        }
    }
}
