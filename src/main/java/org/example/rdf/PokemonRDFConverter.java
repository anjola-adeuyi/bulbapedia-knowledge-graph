package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
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
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonId);

        pokemonResource.addProperty(RDF.type, model.createResource(BASE_URI + "Pokemon"));

        // Add type-specific class
        if (pokemonInfo.containsKey("type1")) {
          Resource typeClass = model.createResource(BASE_URI + "Type/" + pokemonInfo.get("type1"));
          typeClass.addProperty(RDFS.subClassOf, model.createResource(BASE_URI + "Pokemon"));
          pokemonResource.addProperty(RDF.type, typeClass);
      }

        // Add proper owl:sameAs links
        if (pokemonInfo.containsKey("dbpedia_uri")) {
            pokemonResource.addProperty(OWL.sameAs, 
                model.createResource(pokemonInfo.get("dbpedia_uri")));
        }

        // Basic properties using schema.org vocabulary
        addStringProperty(model, pokemonResource, SCHEMA_URI + "name", pokemonInfo.get("name"));
        addStringProperty(model, pokemonResource, SCHEMA_URI + "identifier", pokemonId);
        
        // Physical characteristics
        addDecimalProperty(model, pokemonResource, SCHEMA_URI + "height", pokemonInfo.get("height-m"));
        addDecimalProperty(model, pokemonResource, SCHEMA_URI + "weight", pokemonInfo.get("weight-kg"));

        // Evolution chain info - Make sure evolutionStage is properly typed
        if (pokemonInfo.containsKey("evolutionStage")) {
            try {
                int stage = Integer.parseInt(pokemonInfo.get("evolutionStage"));
                pokemonResource.addProperty(
                    model.createProperty(BASE_URI + "evolutionStage"),
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
        addStringProperty(model, pokemonResource, BASE_URI + "japaneseName", pokemonInfo.get("jname"));
        addStringProperty(model, pokemonResource, BASE_URI + "romajiName", pokemonInfo.get("tmname"));
        
        // Clean and add category
        String category = pokemonInfo.get("category");
        if (category != null && category.startsWith("{{tt|")) {
            category = category.substring(5, category.indexOf("|"));
        }
        addStringProperty(model, pokemonResource, BASE_URI + "category", category);

        // Add types
        addStringProperty(model, pokemonResource, BASE_URI + "primaryType", pokemonInfo.get("type1"));
        if (pokemonInfo.containsKey("type2")) {
            addStringProperty(model, pokemonResource, BASE_URI + "secondaryType", pokemonInfo.get("type2"));
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

    private void addStringProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            Property property = model.createProperty(propertyUri);
            resource.addProperty(property, value);
        }
    }

    private void addDecimalProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                java.math.BigDecimal decimalValue = new java.math.BigDecimal(value);
                Property property = model.createProperty(propertyUri);
                resource.addProperty(property, 
                    ResourceFactory.createTypedLiteral(decimalValue));
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
                    model.createTypedLiteral(numericValue, XSD.xint.getURI()));
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
