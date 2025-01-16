package org.example.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.parser.WikiInfoboxParser;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;

public class DataCollectionCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(DataCollectionCoordinator.class);
    
    private final BulbapediaClient client;
    private final WikiInfoboxParser parser;
    private final EvolutionChainFetcher evolutionFetcher;
    private final ExecutorService executor;
    
    private static final int THREAD_POOL_SIZE = 4;
    private static final int BATCH_SIZE = 50;

    public DataCollectionCoordinator() {
        this.client = new BulbapediaClient();
        this.parser = new WikiInfoboxParser();
        this.evolutionFetcher = new EvolutionChainFetcher(client);
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public List<Map<String, String>> collectAllData() {
        List<Map<String, String>> allData = new ArrayList<>();
        
        try {
            // First collect all Pokemon data
            List<Map<String, String>> pokemonData = evolutionFetcher.fetchAllPokemon();
            allData.addAll(pokemonData);
            
            // Process in batches to avoid overwhelming the server
            List<List<Map<String, String>>> batches = splitIntoBatches(pokemonData, BATCH_SIZE);
            
            for (List<Map<String, String>> batch : batches) {
                List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();
                
                for (Map<String, String> pokemon : batch) {
                    CompletableFuture<Map<String, String>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return enrichPokemonData(pokemon);
                        } catch (Exception e) {
                            logger.error("Error enriching Pokemon data: {}", pokemon.get("title"), e);
                            return pokemon;
                        }
                    }, executor);
                    
                    futures.add(future);
                }
                
                // Wait for all futures in the batch to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // Add enriched data
                for (CompletableFuture<Map<String, String>> future : futures) {
                    try {
                        Map<String, String> enrichedData = future.get();
                        if (enrichedData != null) {
                            synchronized (allData) {
                                allData.add(enrichedData);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error getting enriched data", e);
                    }
                }
                
                // Add delay between batches
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            logger.error("Error collecting data", e);
        } finally {
            shutdown();
        }
        
        return allData;
    }

    private Map<String, String> enrichPokemonData(Map<String, String> pokemon) throws Exception {
        // Get additional data from linked pages
        if (pokemon.containsKey("evolvesFrom")) {
            String prevoPage = pokemon.get("evolvesFrom") + "_(Pokémon)";
            try {
                Map<String, String> prevoData = parser.processWikitext(
                    Collections.singletonMap("wikitext", 
                        client.getPageContent(prevoPage)
                             .getJSONObject("parse")
                             .getJSONObject("wikitext")
                             .getString("*")));
                pokemon.put("prevoPokemon", prevoData.get("name"));
                pokemon.put("prevoNdex", prevoData.get("ndex"));
            } catch (Exception e) {
                logger.warn("Could not fetch previous evolution data for: {}", pokemon.get("title"));
            }
        }

        // Get move data
        try {
            JSONObject moveTemplates = client.getTemplates(pokemon.get("title"));
            // Process move templates...
        } catch (Exception e) {
            logger.warn("Could not fetch move data for: {}", pokemon.get("title"));
        }

        return pokemon;
    }

    private List<List<Map<String, String>>> splitIntoBatches(List<Map<String, String>> data, int batchSize) {
        List<List<Map<String, String>>> batches = new ArrayList<>();
        for (int i = 0; i < data.size(); i += batchSize) {
            batches.add(data.subList(i, Math.min(i + batchSize, data.size())));
        }
        return batches;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
