package org.example.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.DC;
import org.json.JSONObject;

public class PokemonRDFGenerator {
    private static final String BASE_URI = "http://example.org/pokemon/";
    private final Model model;

    public PokemonRDFGenerator() {
        this.model = ModelFactory.createDefaultModel();
        // Set common prefixes
        model.setNsPrefix("pokemon", BASE_URI);
        model.setNsPrefix("schema", "http://schema.org/");
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("rdf", RDF.getURI());
        model.setNsPrefix("dc", DC.getURI());
    }

    public void addPokemon(JSONObject infobox) {
        // Create resource for the Pokemon
        String pokemonName = infobox.getString("name");
        Resource pokemonResource = model.createResource(BASE_URI + "pokemon/" + pokemonName.toLowerCase());

        // Add basic properties
        pokemonResource.addProperty(RDF.type, model.createResource(BASE_URI + "Pokemon"))
                      .addProperty(RDFS.label, pokemonName)
                      .addProperty(model.createProperty("http://schema.org/name"), pokemonName);

        // Add other properties from infobox
        if (infobox.has("number")) {
            pokemonResource.addProperty(
                model.createProperty(BASE_URI + "pokedexNumber"),
                String.valueOf(infobox.getInt("number"))
            );
        }
    }

    public Model getModel() {
        return model;
    }

    public void saveToFile(String filename) {
        try {
            model.write(System.out, "TURTLE");
            model.write(new java.io.FileWriter(filename), "TURTLE");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}