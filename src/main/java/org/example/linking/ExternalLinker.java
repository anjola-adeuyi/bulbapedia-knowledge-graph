package org.example.linking;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;

public class ExternalLinker {
    private static final Logger logger = LoggerFactory.getLogger(ExternalLinker.class);
    private static final Map<String, String> POKEMON_WIKIDATA_IDS = new HashMap<>();
    
    static {
        // Grass starter line
        POKEMON_WIKIDATA_IDS.put("Bulbasaur", "Q1410");
        POKEMON_WIKIDATA_IDS.put("Ivysaur", "Q1411");
        POKEMON_WIKIDATA_IDS.put("Venusaur", "Q1412");
        
        // Fire starter line
        POKEMON_WIKIDATA_IDS.put("Charmander", "Q1416");
        POKEMON_WIKIDATA_IDS.put("Charmeleon", "Q1417");
        POKEMON_WIKIDATA_IDS.put("Charizard", "Q1418");
        
        // Water starter line
        POKEMON_WIKIDATA_IDS.put("Squirtle", "Q1420");
        POKEMON_WIKIDATA_IDS.put("Wartortle", "Q1421");
        POKEMON_WIKIDATA_IDS.put("Blastoise", "Q1422");
    }

    public void addExternalLinks(Model model) {
        ResIterator pokemonIterator = model.listResourcesWithProperty(
            model.createProperty("http://schema.org/name"));

        while (pokemonIterator.hasNext()) {
            Resource pokemon = pokemonIterator.next();
            String pokemonName = pokemon.getProperty(
                model.createProperty("http://schema.org/name"))
                .getString();

            // Add Wikidata link
            String wikidataId = POKEMON_WIKIDATA_IDS.get(pokemonName);
            if (wikidataId != null) {
                String wikidataUri = "http://www.wikidata.org/entity/" + wikidataId;
                pokemon.addProperty(OWL.sameAs, 
                    model.createResource(wikidataUri));
                logger.info("Added Wikidata link for {}: {}", 
                    pokemonName, wikidataUri);
                
                // Add DBpedia link
                String dbpediaUri = "http://dbpedia.org/resource/" + pokemonName.replace(" ", "_");
                pokemon.addProperty(OWL.sameAs, 
                    model.createResource(dbpediaUri));
                logger.info("Added DBpedia link for {}: {}", 
                    pokemonName, dbpediaUri);

                // Add Wikipedia link 
                String wikiUri = "https://en.wikipedia.org/wiki/" + pokemonName.replace(" ", "_");
                pokemon.addProperty(model.createProperty("http://schema.org/sameAs"),
                    model.createResource(wikiUri));
                logger.info("Added Wikipedia link for {}: {}", 
                    pokemonName, wikiUri);
            } else {
                logger.warn("No external IDs found for: {}", pokemonName);
            }
        }
    }
}
