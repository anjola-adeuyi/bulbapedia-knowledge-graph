package org.example.inference;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class InferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(InferenceHandler.class);

    public static Model addInferenceRules(Model baseModel) {
        Model inferenceModel = ModelFactory.createDefaultModel();
        inferenceModel.add(baseModel);

        // Add Pokemon type hierarchy
        addPokemonTypeHierarchy(inferenceModel);

        // Enhanced sameAs closure with property inheritance
        addEnhancedSameAsClosure(inferenceModel);

        // Add characteristic property hierarchy
        addCharacteristicHierarchy(inferenceModel);

        // Add all other inferences
        addTransitiveSubClassClosure(inferenceModel);
        addTypeInference(inferenceModel);
        addPropertyInheritance(inferenceModel);

        logger.info("Added inference rules. Original size: {}, New size: {}", 
            baseModel.size(), inferenceModel.size());
        
        return inferenceModel;
    }

    private static void addPokemonTypeHierarchy(Model model) {
        // Define base Pokemon class
        Resource pokemonClass = model.createResource("http://example.org/pokemon/Pokemon");
        
        // Define type classes with proper hierarchy
        String[] types = {"Fire", "Water", "Grass", "Electric", "Dragon"};
        for (String type : types) {
            Resource typeClass = model.createResource("http://example.org/pokemon/Type/" + type);
            typeClass.addProperty(RDFS.subClassOf, pokemonClass);
            
            // Add type characteristics
            Property hasType = model.createProperty("http://example.org/pokemon/hasType");
            typeClass.addProperty(hasType, type);
        }
    }

    private static void addCharacteristicHierarchy(Model model) {
        // Create base characteristic property
        Property characteristic = model.createProperty("http://example.org/pokemon/characteristic");
        
        // Define sub-properties
        Property[] properties = {
            model.createProperty("http://example.org/pokemon/height"),
            model.createProperty("http://example.org/pokemon/weight"),
            model.createProperty("http://example.org/pokemon/category"),
            model.createProperty("http://example.org/pokemon/ability")
        };
        
        for (Property prop : properties) {
            model.add(prop, RDFS.subPropertyOf, characteristic);
        }
    }

    private static void addEnhancedSameAsClosure(Model model) {
        boolean changed;
        do {
            changed = false;
            StmtIterator iter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
            List<Statement> newStatements = new ArrayList<>();
            
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                if (stmt.getObject().isResource()) {
                    // Symmetric
                    if (!model.contains(stmt.getObject().asResource(), OWL.sameAs, stmt.getSubject())) {
                        newStatements.add(model.createStatement(
                            stmt.getObject().asResource(), OWL.sameAs, stmt.getSubject()));
                        changed = true;
                    }
                    
                    // Transitive
                    StmtIterator sameAsIter = model.listStatements(
                        stmt.getObject().asResource(), OWL.sameAs, (RDFNode)null);
                    while (sameAsIter.hasNext()) {
                        Statement sameAsStmt = sameAsIter.next();
                        if (!model.contains(stmt.getSubject(), OWL.sameAs, sameAsStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                stmt.getSubject(), OWL.sameAs, sameAsStmt.getObject()));
                            changed = true;
                        }
                    }
                    
                    // Property inheritance
                    StmtIterator propIter = model.listStatements(
                        stmt.getSubject(), null, (RDFNode)null);
                    while (propIter.hasNext()) {
                        Statement propStmt = propIter.next();
                        if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                            if (!model.contains(stmt.getObject().asResource(), 
                                            propStmt.getPredicate(), 
                                            propStmt.getObject())) {
                                newStatements.add(model.createStatement(
                                    stmt.getObject().asResource(),
                                    propStmt.getPredicate(),
                                    propStmt.getObject()));
                                changed = true;
                            }
                        }
                    }
                }
            }
            model.add(newStatements);
        } while (changed);
    }


    private static void addTransitiveSubClassClosure(Model model) {
        boolean changed;
        do {
            changed = false;
            StmtIterator iter = model.listStatements(null, RDFS.subClassOf, (RDFNode)null);
            List<Statement> newStatements = new ArrayList<>();
            
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                if (stmt.getObject().isResource()) {
                    StmtIterator superIter = model.listStatements(
                        stmt.getObject().asResource(), RDFS.subClassOf, (RDFNode)null);
                    while (superIter.hasNext()) {
                        Statement superStmt = superIter.next();
                        if (!model.contains(stmt.getSubject(), RDFS.subClassOf, superStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                stmt.getSubject(), RDFS.subClassOf, superStmt.getObject()));
                            changed = true;
                        }
                    }
                }
            }
            model.add(newStatements);
        } while (changed);
    }

    private static void addTypeInference(Model model) {
        StmtIterator typeIter = model.listStatements(null, RDF.type, (RDFNode)null);
        List<Statement> newStatements = new ArrayList<>();
        
        while (typeIter.hasNext()) {
            Statement typeStmt = typeIter.next();
            if (typeStmt.getObject().isResource()) {
                StmtIterator superIter = model.listStatements(
                    typeStmt.getObject().asResource(), RDFS.subClassOf, (RDFNode)null);
                while (superIter.hasNext()) {
                    Statement superStmt = superIter.next();
                    newStatements.add(model.createStatement(
                        typeStmt.getSubject(), RDF.type, superStmt.getObject()));
                }
            }
        }
        model.add(newStatements);
    }

    private static void addPropertyInheritance(Model model) {
        StmtIterator sameAsIter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
        List<Statement> newStatements = new ArrayList<>();
        
        while (sameAsIter.hasNext()) {
            Statement sameAsStmt = sameAsIter.next();
            if (sameAsStmt.getObject().isResource()) {
                Resource subject = sameAsStmt.getSubject();
                Resource object = sameAsStmt.getObject().asResource();
                
                // Copy properties from subject to object
                StmtIterator propIter = model.listStatements(subject, null, (RDFNode)null);
                while (propIter.hasNext()) {
                    Statement propStmt = propIter.next();
                    if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                        newStatements.add(model.createStatement(
                            object, propStmt.getPredicate(), propStmt.getObject()));
                    }
                }
                
                // Copy properties from object to subject
                propIter = model.listStatements(object, null, (RDFNode)null);
                while (propIter.hasNext()) {
                    Statement propStmt = propIter.next();
                    if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                        newStatements.add(model.createStatement(
                            subject, propStmt.getPredicate(), propStmt.getObject()));
                    }
                }
            }
        }
        model.add(newStatements);
    }
}
