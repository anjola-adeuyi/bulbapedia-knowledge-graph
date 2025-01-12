package org.example.client;

import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvolutionChainFetcher {
    private static final Logger logger = LoggerFactory.getLogger(EvolutionChainFetcher.class);
    private final BulbapediaClient client;
    
    // Evolution chains for starter Pokemon
    private static final Map<String, List<String>> EVOLUTION_CHAINS = new HashMap<>();
    
    static {
        // Grass starters
        EVOLUTION_CHAINS.put("Bulbasaur", Arrays.asList(
            "Bulbasaur_(Pokémon)",
            "Ivysaur_(Pokémon)",
            "Venusaur_(Pokémon)"
        ));
        
        // Fire starters
        EVOLUTION_CHAINS.put("Charmander", Arrays.asList(
            "Charmander_(Pokémon)",
            "Charmeleon_(Pokémon)",
            "Charizard_(Pokémon)"
        ));
        
        // Water starters
        EVOLUTION_CHAINS.put("Squirtle", Arrays.asList(
            "Squirtle_(Pokémon)",
            "Wartortle_(Pokémon)",
            "Blastoise_(Pokémon)"
        ));
    }

    public EvolutionChainFetcher(BulbapediaClient client) {
        this.client = client;
    }

    public List<Map<String, String>> fetchEvolutionChain() {
        List<Map<String, String>> allPokemonData = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : EVOLUTION_CHAINS.entrySet()) {
            String starterName = entry.getKey();
            List<String> chain = entry.getValue();
            logger.info("Fetching evolution chain for {} starter", starterName);
            
            for (int i = 0; i < chain.size(); i++) {
                String pokemonName = chain.get(i);
                try {
                    logger.info("Fetching data for {}", pokemonName);
                    JSONObject response = client.getPageContent(pokemonName);
                    
                    // Store evolution stage in additional metadata
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("evolutionStage", String.valueOf(i + 1));
                    if (i > 0) {
                        metadata.put("evolvesFrom", chain.get(i - 1));
                    }
                    
                    // Add the response to our list
                    if (response.has("parse")) {
                        JSONObject parseData = response.getJSONObject("parse");
                        String wikitext = parseData.getJSONObject("wikitext").getString("*");
                        metadata.put("wikitext", wikitext);
                        metadata.put("pageid", String.valueOf(parseData.getInt("pageid")));
                        metadata.put("title", parseData.getString("title"));
                    }
                    
                    allPokemonData.add(metadata);
                    
                } catch (IOException | InterruptedException e) {
                    logger.error("Error fetching data for {}", pokemonName, e);
                }
            }
        }
        
        return allPokemonData;
    }
}
