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

    // Add URI constants
    private static final String BASE_URI = "http://example.org/pokemon/";
    private static final String SCHEMA_URI = "http://schema.org/";

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

    private static void addSameAsInference(Model model) {
      // Add symmetric and transitive closure for owl:sameAs
      boolean changed;
      do {
          changed = false;
          StmtIterator iter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
          List<Statement> newStatements = new ArrayList<>();
          
          while (iter.hasNext()) {
              Statement stmt = iter.next();
              Resource subject = stmt.getSubject();
              Resource object = stmt.getObject().asResource();
              
              // Symmetric property
              if (!model.contains(object, OWL.sameAs, subject)) {
                  newStatements.add(model.createStatement(object, OWL.sameAs, subject));
                  changed = true;
              }
              
              // Transfer schema:name properties
              StmtIterator nameIter = subject.listProperties(model.createProperty(SCHEMA_URI + "name"));
              while (nameIter.hasNext()) {
                  Statement nameStmt = nameIter.next();
                  if (!model.contains(object, nameStmt.getPredicate(), nameStmt.getObject())) {
                      newStatements.add(model.createStatement(
                          object, nameStmt.getPredicate(), nameStmt.getObject()));
                      changed = true;
                  }
              }
              
              // Transitive property
              StmtIterator sameAsIter = object.listProperties(OWL.sameAs);
              while (sameAsIter.hasNext()) {
                  Statement transitiveStmt = sameAsIter.next();
                  if (!model.contains(subject, OWL.sameAs, transitiveStmt.getObject())) {
                      newStatements.add(model.createStatement(
                          subject, OWL.sameAs, transitiveStmt.getObject()));
                      changed = true;
                  }
              }
          }
          model.add(newStatements);
      } while (changed);
    }

    private static void addTypeHierarchyInference(Model model) {
      // Add Pokemon type hierarchy
      Resource pokemonClass = model.createResource(BASE_URI + "Pokemon");
      
      // Define all Pokemon types
      String[] types = {
          "Normal", "Fire", "Water", "Electric", "Grass", "Ice", 
          "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", 
          "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
      };
      
      // Create type classes with hierarchy
      for (String type : types) {
          Resource typeClass = model.createResource(BASE_URI + "Type/" + type);
          // Make type a subclass of Pokemon
          typeClass.addProperty(RDFS.subClassOf, pokemonClass);
          
          // Add primary type as a characteristic
          Property primaryType = model.createProperty(BASE_URI + "primaryType");
          typeClass.addProperty(primaryType, type);
          
          // Find all Pokemon of this type and add type class membership
          StmtIterator pokemonIter = model.listStatements(null, primaryType, type);
          while (pokemonIter.hasNext()) {
              Statement stmt = pokemonIter.next();
              stmt.getSubject().addProperty(RDF.type, typeClass);
          }
      }
      
      // Now add inference for type hierarchy
      addTransitiveSubClassClosure(model);
      addTypeInference(model);
    }

    public static Model addInferenceRules(Model baseModel) {
      Model inferenceModel = ModelFactory.createDefaultModel();
      inferenceModel.add(baseModel);
      
      // Add inference rules in specific order
      addTypeHierarchyInference(inferenceModel);
      addSameAsInference(inferenceModel);
      addCharacteristicHierarchy(inferenceModel);
      
      return inferenceModel;
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
}
