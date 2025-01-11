package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class PokemonRDFConverter {
    private static final Logger logger = LoggerFactory.getLogger(PokemonRDFConverter.class);
    private static final String BASE_URI = "http://example.org/pokemon/";
    private static final String SCHEMA_URI = "http://schema.org/";
    private final Model model;

    public PokemonRDFConverter() {
        this.model = ModelFactory.createDefaultModel();
        // Set common prefixes
        model.setNsPrefix("pokemon", BASE_URI);
        model.setNsPrefix("schema", SCHEMA_URI);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("rdf", RDF.getURI());
    }

    public Model convertToRDF(Map<String, String> pokemonInfo) {
        // Create a new model for this Pokemon
        Model pokemonModel = ModelFactory.createDefaultModel();
        pokemonModel.setNsPrefixes(model.getNsPrefixMap());

        // Create resource for the Pokemon
        String pokemonId = pokemonInfo.getOrDefault("ndex", "0000");
        Resource pokemonResource = pokemonModel.createResource(BASE_URI + "pokemon/" + pokemonId)
            .addProperty(RDF.type, pokemonModel.createResource(BASE_URI + "Pokemon"));

        // Basic properties using schema.org vocabulary
        addProperty(pokemonModel, pokemonResource, SCHEMA_URI + "name", pokemonInfo.get("name"));
        addProperty(pokemonModel, pokemonResource, SCHEMA_URI + "identifier", pokemonInfo.get("ndex"));
        
        // Pokemon-specific properties
        addProperty(pokemonModel, pokemonResource, BASE_URI + "japaneseName", pokemonInfo.get("jname"));
        addProperty(pokemonModel, pokemonResource, BASE_URI + "romajiName", pokemonInfo.get("tmname"));
        addProperty(pokemonModel, pokemonResource, BASE_URI + "category", pokemonInfo.get("category"));
        
        // Types
        addProperty(pokemonModel, pokemonResource, BASE_URI + "primaryType", pokemonInfo.get("type1"));
        if (pokemonInfo.containsKey("type2")) {
            addProperty(pokemonModel, pokemonResource, BASE_URI + "secondaryType", pokemonInfo.get("type2"));
        }

        // Physical characteristics
        addProperty(pokemonModel, pokemonResource, SCHEMA_URI + "height", pokemonInfo.get("height-m"));
        addProperty(pokemonModel, pokemonResource, SCHEMA_URI + "weight", pokemonInfo.get("weight-kg"));

        // Game mechanics
        addProperty(pokemonModel, pokemonResource, BASE_URI + "baseExperienceYield", pokemonInfo.get("expyield"));
        addProperty(pokemonModel, pokemonResource, BASE_URI + "catchRate", pokemonInfo.get("catchrate"));
        addProperty(pokemonModel, pokemonResource, BASE_URI + "generation", pokemonInfo.get("generation"));

        // Evolution chain info
        addProperty(pokemonModel, pokemonResource, BASE_URI + "evolutionStage", pokemonInfo.get("evolutionStage"));
        addProperty(pokemonModel, pokemonResource, BASE_URI + "evolvesFrom", pokemonInfo.get("evolvesFrom"));

        // Abilities
        if (pokemonInfo.containsKey("ability1")) {
            Resource ability = pokemonModel.createResource(BASE_URI + "ability/" + 
                pokemonInfo.get("ability1").toLowerCase().replace(" ", "_"))
                .addProperty(RDFS.label, pokemonInfo.get("ability1"));
            pokemonResource.addProperty(
                pokemonModel.createProperty(BASE_URI + "primaryAbility"), 
                ability
            );
        }

        return pokemonModel;
    }

    private void addProperty(Model model, Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            Property property = model.createProperty(propertyUri);
            resource.addProperty(property, value);
        }
    }

    public void saveToFile(String filename) {
        saveModel(model, filename);
    }

    public void saveModel(Model model, String filename) {
        try {
            // Write the model in Turtle format
            logger.info("Saving RDF data to {}", filename);
            model.write(new java.io.FileWriter(filename), "TURTLE");
        } catch (java.io.IOException e) {
            logger.error("Error saving RDF data to file:", e);
        }
    }

    public Model getModel() {
        return model;
    }
}
