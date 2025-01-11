package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class PokemonRDFConverter {
    private static final Logger logger = LoggerFactory.getLogger(PokemonRDFConverter.class);
    private static final String BASE_URI = "http://example.org/pokemon/";
    private static final String SCHEMA_URI = "http://schema.org/";

    public Model convertToRDF(Map<String, String> pokemonInfo) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("pokemon", BASE_URI);
        model.setNsPrefix("schema", SCHEMA_URI);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("rdf", RDF.getURI());
        model.setNsPrefix("xsd", XSD.getURI());

        // Create resource for the Pokemon
        String pokemonId = pokemonInfo.getOrDefault("ndex", "0000");
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonId)
            .addProperty(RDF.type, model.createResource(BASE_URI + "Pokemon"));

        // Basic properties using schema.org vocabulary
        addStringProperty(model, pokemonResource, SCHEMA_URI + "name", pokemonInfo.get("name"));
        addStringProperty(model, pokemonResource, SCHEMA_URI + "identifier", pokemonInfo.get("ndex"));
        
        // Physical characteristics with proper decimal type
        addDecimalProperty(model, pokemonResource, SCHEMA_URI + "height", pokemonInfo.get("height-m"));
        addDecimalProperty(model, pokemonResource, SCHEMA_URI + "weight", pokemonInfo.get("weight-kg"));

        // Pokemon-specific properties
        addStringProperty(model, pokemonResource, BASE_URI + "japaneseName", pokemonInfo.get("jname"));
        addStringProperty(model, pokemonResource, BASE_URI + "romajiName", pokemonInfo.get("tmname"));
        addStringProperty(model, pokemonResource, BASE_URI + "category", pokemonInfo.get("category"));
        
        // Types
        addStringProperty(model, pokemonResource, BASE_URI + "primaryType", pokemonInfo.get("type1"));
        if (pokemonInfo.containsKey("type2")) {
            addStringProperty(model, pokemonResource, BASE_URI + "secondaryType", pokemonInfo.get("type2"));
        }

        // Game mechanics
        addIntegerProperty(model, pokemonResource, BASE_URI + "baseExperienceYield", pokemonInfo.get("expyield"));
        addIntegerProperty(model, pokemonResource, BASE_URI + "catchRate", pokemonInfo.get("catchrate"));
        addIntegerProperty(model, pokemonResource, BASE_URI + "generation", pokemonInfo.get("generation"));

        // Evolution chain info
        if (pokemonInfo.containsKey("evolutionStage")) {
            addIntegerProperty(model, pokemonResource, BASE_URI + "evolutionStage", pokemonInfo.get("evolutionStage"));
        }
        if (pokemonInfo.containsKey("evolvesFrom")) {
            addStringProperty(model, pokemonResource, BASE_URI + "evolvesFrom", pokemonInfo.get("evolvesFrom"));
        }

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

    private void addStringProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            Property property = model.createProperty(propertyUri);
            resource.addProperty(property, value);
        }
    }

    private void addDecimalProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                double numericValue = Double.parseDouble(value);
                Property property = model.createProperty(propertyUri);
                resource.addProperty(property, 
                    model.createTypedLiteral(numericValue));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse decimal value: {} for property: {}", value, propertyUri);
            }
        }
    }

    private void addIntegerProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                int numericValue = Integer.parseInt(value);
                Property property = model.createProperty(propertyUri);
                resource.addProperty(property, 
                    model.createTypedLiteral(numericValue));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse integer value: {} for property: {}", value, propertyUri);
            }
        }
    }

    public void saveModel(Model model, String filename) {
        try {
            logger.info("Saving RDF data to {}", filename);
            model.write(new java.io.FileWriter(filename), "TURTLE");
        } catch (java.io.IOException e) {
            logger.error("Error saving RDF data:", e);
        }
    }
}
