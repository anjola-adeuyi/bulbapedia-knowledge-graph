package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        
        try {
            BulbapediaClient client = new BulbapediaClient();
            var response = client.getPageContent("Bulbasaur");
            logger.info("Retrieved Bulbasaur page content successfully");
            
        } catch (Exception e) {
            logger.error("Error occurred:", e);
        }
    }
}
