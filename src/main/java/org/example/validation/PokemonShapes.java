package org.example.validation;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PokemonShapes {
    private static final Logger logger = LoggerFactory.getLogger(PokemonShapes.class);
    private static final String SHAPES_URI = "http://example.org/pokemon/shapes/";
    private static final String BASE_URI = "http://example.org/pokemon/";
    private static final String SCHEMA_URI = "http://schema.org/";
    private static final String SH = "http://www.w3.org/ns/shacl#";

    public Model createShapes() {
        Model shapesModel = ModelFactory.createDefaultModel();
        
        // Set up namespaces
        shapesModel.setNsPrefix("sh", SH);
        shapesModel.setNsPrefix("pokemon", BASE_URI);
        shapesModel.setNsPrefix("schema", SCHEMA_URI);
        shapesModel.setNsPrefix("shapes", SHAPES_URI);

        // Create Pokemon Shape
        Resource pokemonShape = shapesModel.createResource(SHAPES_URI + "PokemonShape")
            .addProperty(RDF.type, shapesModel.createResource(SH + "NodeShape"))
            .addProperty(shapesModel.createProperty(SH + "targetClass"), 
                        shapesModel.createResource(BASE_URI + "Pokemon"));

        // Required Properties
        addPropertyConstraint(shapesModel, pokemonShape, SCHEMA_URI + "name", true);
        addPropertyConstraint(shapesModel, pokemonShape, SCHEMA_URI + "identifier", true);
        addPropertyConstraint(shapesModel, pokemonShape, BASE_URI + "primaryType", true);
        
        // Height constraint with proper typing
        Resource heightShape = shapesModel.createResource()
            .addProperty(shapesModel.createProperty(SH + "path"), 
                        shapesModel.createProperty(SCHEMA_URI + "height"))
            .addProperty(shapesModel.createProperty(SH + "datatype"), 
                        XSD.decimal)
            .addProperty(shapesModel.createProperty(SH + "minInclusive"), 
                        shapesModel.createTypedLiteral(0.1))
            .addProperty(shapesModel.createProperty(SH + "maxInclusive"), 
                        shapesModel.createTypedLiteral(25.0));
        pokemonShape.addProperty(shapesModel.createProperty(SH + "property"), heightShape);

        // Weight constraint with proper typing
        Resource weightShape = shapesModel.createResource()
            .addProperty(shapesModel.createProperty(SH + "path"), 
                        shapesModel.createProperty(SCHEMA_URI + "weight"))
            .addProperty(shapesModel.createProperty(SH + "datatype"), 
                        XSD.decimal)
            .addProperty(shapesModel.createProperty(SH + "minInclusive"), 
                        shapesModel.createTypedLiteral(0.1))
            .addProperty(shapesModel.createProperty(SH + "maxInclusive"), 
                        shapesModel.createTypedLiteral(1000.0));
        pokemonShape.addProperty(shapesModel.createProperty(SH + "property"), weightShape);

        // Pokemon Type Values
        Resource typeShape = shapesModel.createResource()
            .addProperty(shapesModel.createProperty(SH + "path"), 
                        shapesModel.createProperty(BASE_URI + "primaryType"))
            .addProperty(shapesModel.createProperty(SH + "in"), 
                        createTypeList(shapesModel));
        pokemonShape.addProperty(shapesModel.createProperty(SH + "property"), typeShape);

        return shapesModel;
    }

    private void addPropertyConstraint(Model model, Resource nodeShape, String propertyUri, boolean required) {
        Resource propertyShape = model.createResource()
            .addProperty(model.createProperty(SH + "path"), 
                        model.createProperty(propertyUri));
        
        if (required) {
            propertyShape.addProperty(model.createProperty(SH + "minCount"), 
                                    model.createTypedLiteral(1));
        }
        
        nodeShape.addProperty(model.createProperty(SH + "property"), propertyShape);
    }

    private RDFList createTypeList(Model model) {
        return model.createList(new RDFNode[] {
            model.createLiteral("Normal"),
            model.createLiteral("Fire"),
            model.createLiteral("Water"),
            model.createLiteral("Electric"),
            model.createLiteral("Grass"),
            model.createLiteral("Ice"),
            model.createLiteral("Fighting"),
            model.createLiteral("Poison"),
            model.createLiteral("Ground"),
            model.createLiteral("Flying"),
            model.createLiteral("Psychic"),
            model.createLiteral("Bug"),
            model.createLiteral("Rock"),
            model.createLiteral("Ghost"),
            model.createLiteral("Dragon"),
            model.createLiteral("Dark"),
            model.createLiteral("Steel"),
            model.createLiteral("Fairy")
        });
    }

    public void saveShapes(String filename) {
        Model shapes = createShapes();
        try {
            logger.info("Saving SHACL shapes to {}", filename);
            shapes.write(new java.io.FileWriter(filename), "TURTLE");
        } catch (java.io.IOException e) {
            logger.error("Error saving SHACL shapes:", e);
        }
    }
}
