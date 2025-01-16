package org.example.client;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EvolutionChainFetcher {
    private static final Logger logger = LoggerFactory.getLogger(EvolutionChainFetcher.class);
    private final BulbapediaClient client;
    private final Map<String, String> processedPages;
    private static final int DELAY_MS = 1000; // 1 second delay between requests
    
    // Categories to process
    private static final List<String> POKEMON_CATEGORIES = Arrays.asList(
        "Generation_I_Pokémon",
        "Generation_II_Pokémon",
        "Generation_III_Pokémon",
        "Generation_IV_Pokémon",
        "Generation_V_Pokémon"
    );

    public EvolutionChainFetcher(BulbapediaClient client) {
        this.client = client;
        this.processedPages = new ConcurrentHashMap<>();
    }

    public List<Map<String, String>> fetchAllPokemon() {
        List<Map<String, String>> allPokemonData = new ArrayList<>();
        
        for (String category : POKEMON_CATEGORIES) {
            try {
                logger.info("Fetching Pokemon from category: {}", category);
                List<String> pokemonPages = getPokemonFromCategory(category);
                
                for (String pokemonPage : pokemonPages) {
                    if (processedPages.containsKey(pokemonPage)) {
                        continue;
                    }
                    
                    try {
                        Map<String, String> pokemonData = fetchPokemonData(pokemonPage);
                        if (pokemonData != null && !pokemonData.isEmpty()) {
                            allPokemonData.add(pokemonData);
                            processedPages.put(pokemonPage, "processed");
                        }
                        
                        // Add delay to avoid overwhelming the server
                        TimeUnit.MILLISECONDS.sleep(DELAY_MS);
                    } catch (Exception e) {
                        logger.error("Error processing Pokemon page: {}", pokemonPage, e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing category: {}", category, e);
            }
        }
        
        return allPokemonData;
    }

    private List<String> getPokemonFromCategory(String category) throws IOException, InterruptedException {
        List<String> pokemonPages = new ArrayList<>();
        String continueFrom = null;
        
        do {
            JSONObject response = client.queryCategory(category, continueFrom);
            JSONObject query = response.getJSONObject("query");
            
            if (query.has("categorymembers")) {
                JSONArray members = query.getJSONArray("categorymembers");
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.getJSONObject(i);
                    String title = member.getString("title");
                    if (title.endsWith("_(Pokémon)")) {
                        pokemonPages.add(title);
                    }
                }
            }
            
            // Check for continue token
            if (response.has("continue")) {
                continueFrom = response.getJSONObject("continue").getString("cmcontinue");
            } else {
                continueFrom = null;
            }
            
            TimeUnit.MILLISECONDS.sleep(DELAY_MS);
        } while (continueFrom != null);
        
        return pokemonPages;
    }

    private Map<String, String> fetchPokemonData(String pokemonPage) throws IOException, InterruptedException {
        Map<String, String> pokemonData = new HashMap<>();
        
        JSONObject response = client.getPageContent(pokemonPage);
        if (!response.has("parse")) {
            return null;
        }
        
        JSONObject parseData = response.getJSONObject("parse");
        String wikitext = parseData.getJSONObject("wikitext").getString("*");
        
        pokemonData.put("wikitext", wikitext);
        pokemonData.put("pageid", String.valueOf(parseData.getInt("pageid")));
        pokemonData.put("title", parseData.getString("title"));
        
        // Extract Pokemon number from wikitext
        String ndex = extractNdex(wikitext);
        if (ndex != null) {
            pokemonData.put("ndex", ndex);
        }
        
        // Extract evolution stage and chain
        addEvolutionData(pokemonData, wikitext);
        
        return pokemonData;
    }

    private String extractNdex(String wikitext) {
        // Find ndex parameter in wikitext
        int ndexStart = wikitext.indexOf("|ndex=");
        if (ndexStart >= 0) {
            int start = ndexStart + 6;
            int end = wikitext.indexOf("\n", start);
            if (end > start) {
                String ndex = wikitext.substring(start, end).trim();
                // Ensure it's a valid number and pad with zeros
                try {
                    int number = Integer.parseInt(ndex);
                    return String.format("%04d", number);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid ndex value: {}", ndex);
                }
            }
        }
        return null;
    }

    private void addEvolutionData(Map<String, String> pokemonData, String wikitext) {
        // Extract evolution data from wikitext
        if (wikitext.contains("|evointo=")) {
            String[] lines = wikitext.split("\n");
            for (String line : lines) {
                if (line.startsWith("|prevo=")) {
                    String prevo = line.substring(7).trim();
                    if (!prevo.isEmpty() && !prevo.equals("None")) {
                        pokemonData.put("evolvesFrom", prevo);
                    }
                }
                if (line.startsWith("|evointo=")) {
                    String evointo = line.substring(9).trim();
                    if (!evointo.isEmpty() && !evointo.equals("None")) {
                        pokemonData.put("evolvesTo", evointo);
                    }
                }
            }
        }
        
        // Determine evolution stage
        int stage = 1;
        if (pokemonData.containsKey("evolvesFrom")) {
            stage++;
            if (wikitext.contains("|evointo=") && !wikitext.contains("|evointo=None")) {
                stage = 2;
            } else {
                stage = 3;
            }
        }
        pokemonData.put("evolutionStage", String.valueOf(stage));
    }
}
