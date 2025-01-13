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
      boolean changed;
      do {
          changed = false;
          StmtIterator iter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
          List<Statement> newStatements = new ArrayList<>();
          
          while (iter.hasNext()) {
              Statement stmt = iter.next();
              Resource subject = stmt.getSubject();
              Resource object = stmt.getObject().asResource();
              
              // Symmetric
              if (!model.contains(object, OWL.sameAs, subject)) {
                  newStatements.add(model.createStatement(object, OWL.sameAs, subject));
                  changed = true;
              }
              
              // Copy all properties (not just schema:name)
              StmtIterator subjectProps = subject.listProperties();
              while (subjectProps.hasNext()) {
                  Statement propStmt = subjectProps.next();
                  if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                      if (!model.contains(object, propStmt.getPredicate(), propStmt.getObject())) {
                          newStatements.add(model.createStatement(
                              object, propStmt.getPredicate(), propStmt.getObject()));
                          changed = true;
                      }
                  }
              }
              
              // Transitive
              StmtIterator sameAsIter = model.listStatements(object, OWL.sameAs, (RDFNode)null);
              while (sameAsIter.hasNext()) {
                  Statement sameAsStmt = sameAsIter.next();
                  if (!model.contains(subject, OWL.sameAs, sameAsStmt.getObject())) {
                      newStatements.add(model.createStatement(
                          subject, OWL.sameAs, sameAsStmt.getObject()));
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
      Map<String, Resource> typeClasses = new HashMap<>();
      for (String type : types) {
          Resource typeClass = model.createResource(BASE_URI + "Type/" + type);
          typeClasses.put(type, typeClass);
          
          // Make type a subclass of Pokemon
          typeClass.addProperty(RDFS.subClassOf, pokemonClass);
          typeClass.addProperty(model.createProperty(BASE_URI + "primaryType"), type);
      }

      // Find all Pokemon and establish their type relationships
      StmtIterator pokemonIter = model.listStatements(null, 
          model.createProperty(BASE_URI + "primaryType"), (RDFNode)null);

      while (pokemonIter.hasNext()) {
          Statement stmt = pokemonIter.next();
          Resource pokemon = stmt.getSubject();
          String type = stmt.getObject().toString();
          
          // Create specific type class for this Pokemon
          Resource specificType = model.createResource(pokemon.getURI() + "/type");
          specificType.addProperty(RDFS.subClassOf, typeClasses.get(type));
          specificType.addProperty(model.createProperty(BASE_URI + "primaryType"), type);
          
          // Link Pokemon to its specific type and the general type class
          pokemon.addProperty(RDFS.subClassOf, specificType);
          pokemon.addProperty(RDFS.subClassOf, typeClasses.get(type));
          pokemon.addProperty(RDF.type, specificType);
          pokemon.addProperty(RDF.type, typeClasses.get(type));
          pokemon.addProperty(RDF.type, pokemonClass);
      }
    }

    public static Model addInferenceRules(Model baseModel) {
      Model inferenceModel = ModelFactory.createDefaultModel();
      inferenceModel.add(baseModel);
      
      // Add inference rules in specific order
      addTypeHierarchyInference(inferenceModel);
      addSameAsInference(inferenceModel);
      addPropertyInheritance(inferenceModel);
      addCharacteristicHierarchy(inferenceModel);
      
      return inferenceModel;
    }

    private static void addPropertyInheritance(Model model) {
        StmtIterator sameAsIter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
        List<Statement> newStatements = new ArrayList<>();
        
        while (sameAsIter.hasNext()) {
            Statement sameAsStmt = sameAsIter.next();
            if (sameAsStmt.getObject().isResource()) {
                Resource subject = sameAsStmt.getSubject();
                Resource object = sameAsStmt.getObject().asResource();
                
                // Copy all properties from subject to object (except sameAs)
                StmtIterator subjectProps = subject.listProperties();
                while (subjectProps.hasNext()) {
                    Statement propStmt = subjectProps.next();
                    if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                        if (!model.contains(object, propStmt.getPredicate(), propStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                object, propStmt.getPredicate(), propStmt.getObject()));
                        }
                    }
                }
                
                // Copy all properties from object to subject (except sameAs)
                StmtIterator objectProps = object.listProperties();
                while (objectProps.hasNext()) {
                    Statement propStmt = objectProps.next();
                    if (!propStmt.getPredicate().equals(OWL.sameAs)) {
                        if (!model.contains(subject, propStmt.getPredicate(), propStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                subject, propStmt.getPredicate(), propStmt.getObject()));
                        }
                    }
                }
            }
        }
        
        // Add all new statements to the model
        model.add(newStatements);
        
        logger.debug("Added {} property inheritance statements", newStatements.size());
    }
}
