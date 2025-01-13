package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class PokemonRDFConverter {
    private static final Logger logger = LoggerFactory.getLogger(PokemonRDFConverter.class);
    private static final String BASE_URI = "http://example.org/pokemon/";
    private static final String SCHEMA_URI = "http://schema.org/";
    
    // Define schema.org properties
    private static final Property SCHEMA_NAME = ResourceFactory.createProperty(SCHEMA_URI + "name");
    private static final Property SCHEMA_HEIGHT = ResourceFactory.createProperty(SCHEMA_URI + "height");
    private static final Property SCHEMA_WEIGHT = ResourceFactory.createProperty(SCHEMA_URI + "weight");
    private static final Property SCHEMA_IDENTIFIER = ResourceFactory.createProperty(SCHEMA_URI + "identifier");
    
    // Define Pokemon properties
    private static final Property POKEMON_PRIMARY_TYPE = ResourceFactory.createProperty(BASE_URI + "primaryType");
    private static final Property POKEMON_CHARACTERISTIC = ResourceFactory.createProperty(BASE_URI + "characteristic");

    public Model convertToRDF(Map<String, String> pokemonInfo) {
        Model model = ModelFactory.createDefaultModel();
        
        // Set common prefixes
        model.setNsPrefix("pokemon", BASE_URI);
        model.setNsPrefix("schema", SCHEMA_URI);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("owl", OWL.getURI());
    
        String pokemonId = String.format("%04d", 
            Integer.parseInt(pokemonInfo.getOrDefault("ndex", "0")));
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonId);
    
        // Add base type
        pokemonResource.addProperty(RDF.type, model.createResource(BASE_URI + "Pokemon"));
        
        // Add type class membership
        String primaryType = pokemonInfo.get("type1");
        if (primaryType != null) {
            Resource typeClass = model.createResource(BASE_URI + "Type/" + primaryType);
            pokemonResource.addProperty(RDF.type, typeClass);
            pokemonResource.addProperty(POKEMON_PRIMARY_TYPE, primaryType);
            
            // Add explicit type resource
            Resource pokemonType = model.createResource(BASE_URI + 
                pokemonResource.getLocalName() + "/type");
            pokemonType.addProperty(RDFS.subClassOf, typeClass);
            pokemonResource.addProperty(RDF.type, pokemonType);
        }
        
        // Add external links with proper schema:name
        String name = pokemonInfo.get("name");
        if (name != null) {
            // DBpedia link
            String dbpediaUri = "http://dbpedia.org/resource/" + name.replace(" ", "_");
            Resource dbpediaResource = model.createResource(dbpediaUri)
                .addProperty(SCHEMA_NAME, name);
            pokemonResource.addProperty(OWL.sameAs, dbpediaResource);
            
            // Wikidata link
            String wikidataId = getWikidataId(name);
            if (wikidataId != null) {
                Resource wikidataResource = model.createResource(
                    "http://www.wikidata.org/entity/" + wikidataId)
                    .addProperty(SCHEMA_NAME, name);
                pokemonResource.addProperty(OWL.sameAs, wikidataResource);
            }
        }
    
        // Add basic properties with validation
        addValidatedProperty(model, pokemonResource, SCHEMA_NAME, pokemonInfo.get("name"));
        addValidatedProperty(model, pokemonResource, SCHEMA_IDENTIFIER, pokemonId);
        
        // Add physical characteristics
        addDecimalProperty(model, pokemonResource, SCHEMA_HEIGHT, 
            pokemonInfo.get("height-m"), 0.1, 25.0);
        addDecimalProperty(model, pokemonResource, SCHEMA_WEIGHT, 
            pokemonInfo.get("weight-kg"), 0.1, 1000.0);
    
        // Add multilingual labels
        addMultilingualLabels(model, pokemonResource, pokemonInfo);
    
        // Add external links with verification
        addExternalLinks(model, pokemonResource, pokemonInfo);
    
        return model;
    }

    private void addValidatedProperty(Model model, Resource resource, 
      Property property, String value) {
      if (value != null && !value.trim().isEmpty()) {
      resource.addProperty(property, value.trim());
      }
    }

    private void addMultilingualLabels(Model model, Resource resource, 
      Map<String, String> pokemonInfo) {
      String name = pokemonInfo.get("name");
      if (name != null) {
        resource.addProperty(RDFS.label, model.createLiteral(name, "en"));
        }

      String japaneseName = pokemonInfo.get("japanese_name");
      if (japaneseName != null) {
        resource.addProperty(RDFS.label, model.createLiteral(japaneseName, "ja"));
        }

      String romajiName = pokemonInfo.get("romaji_name");
      if (romajiName != null) {
        resource.addProperty(RDFS.label, model.createLiteral(romajiName, "ja-Latn"));
        }
    }

    private void addDecimalProperty(Model model, Resource resource, Property property, 
                                  String value, double min, double max) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                double numericValue = Double.parseDouble(value);
                // Validate range
                if (numericValue >= min && numericValue <= max) {
                    resource.addProperty(property, 
                        model.createTypedLiteral(new BigDecimal(numericValue)));
                } else {
                    logger.warn("Value {} for property {} is outside valid range [{}, {}]", 
                        value, property, min, max);
                }
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse decimal value: {} for property: {}", 
                    value, property);
            }
        }
    }

    private void addExternalLinks(Model model, Resource resource, Map<String, String> pokemonInfo) {
        String name = pokemonInfo.get("name");
        if (name != null) {
            // Add DBpedia link with name
            String dbpediaUri = "http://dbpedia.org/resource/" + name.replace(" ", "_");
            Resource dbpediaResource = model.createResource(dbpediaUri);
            // Add name to DBpedia resource
            dbpediaResource.addProperty(SCHEMA_NAME, name);
            resource.addProperty(OWL.sameAs, dbpediaResource);
    
            // Add Wikidata link if available
            String wikidataId = getWikidataId(name);
            if (wikidataId != null) {
                String wikidataUri = "http://www.wikidata.org/entity/" + wikidataId;
                Resource wikidataResource = model.createResource(wikidataUri);
                // Add name to Wikidata resource
                wikidataResource.addProperty(SCHEMA_NAME, name);
                resource.addProperty(OWL.sameAs, wikidataResource);
            }
    
            // Add Bulbapedia link
            String bulbapediaUri = "https://bulbapedia.bulbagarden.net/wiki/" + 
                name.replace(" ", "_") + "_(Pokémon)";
            Resource bulbapediaResource = model.createResource(bulbapediaUri);
            // Add name to Bulbapedia resource
            bulbapediaResource.addProperty(SCHEMA_NAME, name);
            resource.addProperty(model.createProperty(SCHEMA_URI + "sameAs"), bulbapediaResource);
        }
    }

    private String getWikidataId(String pokemonName) {
        Map<String, String> wikidataIds = new HashMap<>();
        wikidataIds.put("Bulbasaur", "Q1410");
        wikidataIds.put("Ivysaur", "Q1411");
        wikidataIds.put("Venusaur", "Q1412");
        wikidataIds.put("Charmander", "Q1416");
        wikidataIds.put("Charmeleon", "Q1417");
        wikidataIds.put("Charizard", "Q1418");
        wikidataIds.put("Squirtle", "Q1420");
        wikidataIds.put("Wartortle", "Q1421");
        wikidataIds.put("Blastoise", "Q1422");
        return wikidataIds.get(pokemonName);
    }

    public void saveModel(Model model, String filename) {
        try {
            // First check if file exists and has content
            java.io.File file = new java.io.File(filename);
            if (file.exists() && file.length() > 0) {
                // Load existing model
                Model existingModel = ModelFactory.createDefaultModel();
                existingModel.read(new java.io.FileInputStream(file), null, "TURTLE");
                
                // Add new data to existing model
                existingModel.add(model);
                
                // Save combined model
                logger.info("Updating existing RDF data in {}", filename);
                existingModel.write(new java.io.FileWriter(filename), "TURTLE");
            } else {
                // Create new file with model
                logger.info("Saving new RDF data to {}", filename);
                model.write(new java.io.FileWriter(filename), "TURTLE");
            }
        } catch (java.io.IOException e) {
            logger.error("Error saving RDF data:", e);
        }
    }
}
