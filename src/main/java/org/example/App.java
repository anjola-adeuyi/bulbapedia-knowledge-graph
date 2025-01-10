package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.client.BulbapediaClient;
import org.json.JSONObject;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Bulbapedia Knowledge Graph Generator");
        
        try {
            BulbapediaClient client = new BulbapediaClient();
            JSONObject response = client.getPageContent("Bulbasaur");
            
            if (response.has("parse")) {
                JSONObject parseData = response.getJSONObject("parse");
                logger.info("Successfully retrieved page with ID: " + parseData.get("pageid"));
                logger.info("Page title: " + parseData.get("title"));
            } else {
                logger.warn("Unexpected response format: " + response.toString(2));
            }
            
        } catch (Exception e) {
            logger.error("Error occurred:", e);
        }
    }
}
