package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Model convertToRDF(Map<String, String> pokemonInfo) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("pokemon", BASE_URI);
        model.setNsPrefix("schema", SCHEMA_URI);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("rdf", RDF.getURI());
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("owl", OWL.getURI());

        // Create base resource
        String pokemonId = pokemonInfo.getOrDefault("ndex", "0000");
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonId);

        // Add base property hierarchy
        Property characteristicProp = model.createProperty(BASE_URI + "characteristic");
        SCHEMA_HEIGHT.addProperty(RDFS.subPropertyOf, characteristicProp);
        SCHEMA_WEIGHT.addProperty(RDFS.subPropertyOf, characteristicProp);

        // Add type hierarchy
        Resource pokemonClass = model.createResource(BASE_URI + "Pokemon");
        pokemonResource.addProperty(RDF.type, pokemonClass);

        if (pokemonInfo.containsKey("type1")) {
            String type = pokemonInfo.get("type1");
            Resource typeClass = model.createResource(BASE_URI + "Type/" + type);
            typeClass.addProperty(RDFS.subClassOf, pokemonClass);
            pokemonResource.addProperty(RDF.type, typeClass);
        }

        // Add DBpedia and Wikidata links
        String name = pokemonInfo.get("name");
        if (name != null) {
            // DBpedia link
            String dbpediaUri = "http://dbpedia.org/resource/" + name.replace(" ", "_");
            Resource dbpediaResource = model.createResource(dbpediaUri);
            pokemonResource.addProperty(OWL.sameAs, dbpediaResource);
            dbpediaResource.addProperty(SCHEMA_NAME, name);

            // Wikidata link
            String wikidataId = getWikidataId(name);
            if (wikidataId != null) {
                Resource wikidataResource = model.createResource("http://www.wikidata.org/entity/" + wikidataId);
                pokemonResource.addProperty(OWL.sameAs, wikidataResource);
                wikidataResource.addProperty(SCHEMA_NAME, name);
            }
        }

        // Basic properties
        pokemonResource.addProperty(SCHEMA_NAME, pokemonInfo.get("name"));
        pokemonResource.addProperty(SCHEMA_IDENTIFIER, pokemonId);
        
        // Physical characteristics
        addDecimalProperty(model, pokemonResource, SCHEMA_HEIGHT, pokemonInfo.get("height-m"));
        addDecimalProperty(model, pokemonResource, SCHEMA_WEIGHT, pokemonInfo.get("weight-kg"));

        // Evolution chain info
        if (pokemonInfo.containsKey("evolutionStage")) {
            try {
                int stage = Integer.parseInt(pokemonInfo.get("evolutionStage"));
                Property evolutionStageProp = model.createProperty(BASE_URI + "evolutionStage");
                evolutionStageProp.addProperty(RDFS.subPropertyOf, characteristicProp);
                pokemonResource.addProperty(
                    evolutionStageProp,
                    model.createTypedLiteral(stage, XSD.integer.getURI())
                );
                
                if (pokemonInfo.containsKey("evolvesFrom")) {
                    String prevId = pokemonInfo.get("evolvesFrom");
                    Resource prevPokemon = model.createResource(BASE_URI + "pokemon/" + prevId);
                    pokemonResource.addProperty(
                        model.createProperty(BASE_URI + "evolvesFrom"), 
                        prevPokemon
                    );
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid evolution stage value: {}", pokemonInfo.get("evolutionStage"));
            }
        }

        // Pokemon-specific properties
        addCharacteristicProperty(model, pokemonResource, BASE_URI + "japaneseName", pokemonInfo.get("jname"));
        addCharacteristicProperty(model, pokemonResource, BASE_URI + "romajiName", pokemonInfo.get("tmname"));
        
        // Category
        String category = pokemonInfo.get("category");
        if (category != null && category.startsWith("{{tt|")) {
            category = category.substring(5, category.indexOf("|"));
        }
        addCharacteristicProperty(model, pokemonResource, BASE_URI + "category", category);

        // Types
        Property typeProp = model.createProperty(BASE_URI + "primaryType");
        typeProp.addProperty(RDFS.subPropertyOf, characteristicProp);
        addCharacteristicProperty(model, pokemonResource, BASE_URI + "primaryType", pokemonInfo.get("type1"));
        
        if (pokemonInfo.containsKey("type2")) {
            addCharacteristicProperty(model, pokemonResource, BASE_URI + "secondaryType", pokemonInfo.get("type2"));
        }

        // Game mechanics
        addIntegerProperty(model, pokemonResource, BASE_URI + "baseExperienceYield", pokemonInfo.get("expyield"));
        addIntegerProperty(model, pokemonResource, BASE_URI + "catchRate", pokemonInfo.get("catchrate"));
        addIntegerProperty(model, pokemonResource, BASE_URI + "generation", pokemonInfo.get("generation"));

        // Abilities
        if (pokemonInfo.containsKey("ability1")) {
            Resource ability = model.createResource(BASE_URI + "ability/" + 
                pokemonInfo.get("ability1").toLowerCase().replace(" ", "_"))
                .addProperty(RDFS.label, pokemonInfo.get("ability1"));
            pokemonResource.addProperty(
                model.createProperty(BASE_URI + "primaryAbility"), 
                ability
            );
        }

        return model;
    }

    private void addCharacteristicProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            Property property = model.createProperty(propertyUri);
            property.addProperty(RDFS.subPropertyOf, model.createProperty(BASE_URI + "characteristic"));
            resource.addProperty(property, value);
        }
    }

    private void addDecimalProperty(Model model, Resource resource, Property property, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                java.math.BigDecimal decimalValue = new java.math.BigDecimal(value);
                resource.addProperty(property, ResourceFactory.createTypedLiteral(decimalValue));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse decimal value: {} for property: {}", value, property);
            }
        }
    }

    private void addIntegerProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                int numericValue = Integer.parseInt(value);
                Property property = model.createProperty(propertyUri);
                property.addProperty(RDFS.subPropertyOf, model.createProperty(BASE_URI + "characteristic"));
                resource.addProperty(property, 
                    model.createTypedLiteral(numericValue, XSD.xint.getURI()));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse integer value: {} for property: {}", value, propertyUri);
            }
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
