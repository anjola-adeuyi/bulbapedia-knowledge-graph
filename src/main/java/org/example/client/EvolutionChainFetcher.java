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
                    int stage = i + 1;
                    metadata.put("evolutionStage", String.valueOf(stage));
                    
                    // Add evolution data
                    if (i > 0) {
                        String prevPokemonId = String.format("%04d", Integer.parseInt(getPokemonId(chain.get(i - 1))));
                        metadata.put("evolvesFrom", prevPokemonId);
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

    private String getPokemonId(String pokemonName) {
        // Extract Pokemon ID from the wikitext if available
        try {
            JSONObject response = client.getPageContent(pokemonName);
            if (response.has("parse")) {
                String wikitext = response.getJSONObject("parse")
                                       .getJSONObject("wikitext")
                                       .getString("*");
                // Look for ndex parameter
                int ndexStart = wikitext.indexOf("|ndex=");
                if (ndexStart >= 0) {
                    int start = ndexStart + 6;
                    int end = wikitext.indexOf("\n", start);
                    if (end > start) {
                        return wikitext.substring(start, end).trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get Pokemon ID from {}", pokemonName);
        }
        return "0000";
    }
}
