package org.example.client;

import org.json.JSONObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvolutionChainFetcher {
    private static final Logger logger = LoggerFactory.getLogger(EvolutionChainFetcher.class);
    private final BulbapediaClient client;
    
    // Bulbasaur evolution chain
    private static final List<String> EVOLUTION_CHAIN = Arrays.asList(
        "Bulbasaur_(Pokémon)",
        "Ivysaur_(Pokémon)",
        "Venusaur_(Pokémon)"
    );

    public EvolutionChainFetcher(BulbapediaClient client) {
        this.client = client;
    }

    public List<Map<String, String>> fetchEvolutionChain() {
        List<Map<String, String>> pokemonData = new ArrayList<>();
        
        for (String pokemonName : EVOLUTION_CHAIN) {
            try {
                logger.info("Fetching data for " + pokemonName);
                JSONObject response = client.getPageContent(pokemonName);
                
                // Store evolution stage in additional metadata
                Map<String, String> metadata = new HashMap<>();
                metadata.put("evolutionStage", String.valueOf(EVOLUTION_CHAIN.indexOf(pokemonName) + 1));
                metadata.put("evolvesFrom", 
                    EVOLUTION_CHAIN.indexOf(pokemonName) > 0 ? 
                    EVOLUTION_CHAIN.get(EVOLUTION_CHAIN.indexOf(pokemonName) - 1) : null);
                
                // Add the response to our list
                if (response.has("parse")) {
                    JSONObject parseData = response.getJSONObject("parse");
                    String wikitext = parseData.getJSONObject("wikitext").getString("*");
                    metadata.put("wikitext", wikitext);
                    metadata.put("pageid", String.valueOf(parseData.getInt("pageid")));
                    metadata.put("title", parseData.getString("title"));
                }
                
                pokemonData.add(metadata);
                
            } catch (IOException | InterruptedException e) {
                logger.error("Error fetching data for " + pokemonName, e);
            }
        }
        
        return pokemonData;
    }
}
