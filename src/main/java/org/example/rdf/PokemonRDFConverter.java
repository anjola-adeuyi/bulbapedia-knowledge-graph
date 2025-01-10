package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import java.util.Map;

public class PokemonRDFConverter {
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
        // Create resource for the Pokemon
        String pokemonId = pokemonInfo.getOrDefault("ndex", "0000");
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonId)
            .addProperty(RDF.type, model.createResource(BASE_URI + "Pokemon"));

        // Basic properties using schema.org vocabulary
        addProperty(pokemonResource, SCHEMA_URI + "name", pokemonInfo.get("name"));
        addProperty(pokemonResource, SCHEMA_URI + "identifier", pokemonInfo.get("ndex"));
        
        // Pokemon-specific properties
        addProperty(pokemonResource, BASE_URI + "japaneseName", pokemonInfo.get("jname"));
        addProperty(pokemonResource, BASE_URI + "romajiName", pokemonInfo.get("tmname"));
        addProperty(pokemonResource, BASE_URI + "category", pokemonInfo.get("category"));
        
        // Types
        addProperty(pokemonResource, BASE_URI + "primaryType", pokemonInfo.get("type1"));
        if (pokemonInfo.containsKey("type2")) {
            addProperty(pokemonResource, BASE_URI + "secondaryType", pokemonInfo.get("type2"));
        }

        // Physical characteristics
        if (pokemonInfo.containsKey("height-m")) {
            addProperty(pokemonResource, SCHEMA_URI + "height", pokemonInfo.get("height-m"));
        }
        if (pokemonInfo.containsKey("weight-kg")) {
            addProperty(pokemonResource, SCHEMA_URI + "weight", pokemonInfo.get("weight-kg"));
        }

        // Game mechanics
        addProperty(pokemonResource, BASE_URI + "baseExperienceYield", pokemonInfo.get("expyield"));
        addProperty(pokemonResource, BASE_URI + "catchRate", pokemonInfo.get("catchrate"));
        addProperty(pokemonResource, BASE_URI + "generation", pokemonInfo.get("generation"));

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

    private void addProperty(Resource resource, String propertyUri, String value) {
        if (value != null && !value.isEmpty()) {
            Property property = model.createProperty(propertyUri);
            resource.addProperty(property, value);
        }
    }

    public void saveToFile(String filename) {
        try {
            model.write(new java.io.FileWriter(filename), "TURTLE");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public Model getModel() {
        return model;
    }
}
