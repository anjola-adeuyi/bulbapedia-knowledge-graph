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
        Property characteristic = model.createProperty(BASE_URI + "characteristic");
        List<Property> properties = Arrays.asList(
            model.createProperty(BASE_URI + "height"),
            model.createProperty(BASE_URI + "weight"),
            model.createProperty(BASE_URI + "category"),
            model.createProperty(BASE_URI + "ability"),
            model.createProperty(BASE_URI + "primaryType"),
            model.createProperty(BASE_URI + "secondaryType"),
            model.createProperty(SCHEMA_URI + "height"),
            model.createProperty(SCHEMA_URI + "weight")
        );
        
        for (Property prop : properties) {
            model.add(model.createStatement(prop, RDFS.subPropertyOf, characteristic));
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
        Property schemaName = model.createProperty(SCHEMA_URI + "name");
        
        do {
            changed = false;
            List<Statement> newStatements = new ArrayList<>();
            StmtIterator iter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
            
            while (iter.hasNext()) {
                Statement stmt = iter.nextStatement();
                Resource subject = stmt.getSubject();
                Resource object = stmt.getObject().asResource();
                
                // Symmetric property
                if (!model.contains(object, OWL.sameAs, subject)) {
                    newStatements.add(model.createStatement(object, OWL.sameAs, subject));
                    changed = true;
                }
                
                // Copy names both ways
                StmtIterator subjectNames = subject.listProperties(schemaName);
                while (subjectNames.hasNext()) {
                    Statement nameStmt = subjectNames.nextStatement();
                    if (!model.contains(object, schemaName, nameStmt.getObject())) {
                        newStatements.add(model.createStatement(
                            object, schemaName, nameStmt.getObject()));
                        changed = true;
                    }
                }
                
                StmtIterator objectNames = object.listProperties(schemaName);
                while (objectNames.hasNext()) {
                    Statement nameStmt = objectNames.nextStatement();
                    if (!model.contains(subject, schemaName, nameStmt.getObject())) {
                        newStatements.add(model.createStatement(
                            subject, schemaName, nameStmt.getObject()));
                        changed = true;
                    }
                }
            }
            
            if (!newStatements.isEmpty()) {
                model.add(newStatements);
            }
        } while (changed);
    }

    private static void addTypeHierarchyInference(Model model) {
      // Add Pokemon type hierarchy
      List<Statement> newStatements = new ArrayList<>();
      Resource pokemonClass = model.createResource(BASE_URI + "Pokemon");
      
      // Define all Pokemon types
      String[] types = {
          "Normal", "Fire", "Water", "Electric", "Grass", "Ice", 
          "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", 
          "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
      };
      
      // Create type classes with hierarchy
      Map<Resource, String> typesToProcess = new HashMap<>();
      
      // First pass: Create all type classes
      for (String type : types) {
          Resource typeClass = model.createResource(BASE_URI + "Type/" + type);
          typesToProcess.put(typeClass, type);
          // Make type a subclass of Pokemon
          newStatements.add(model.createStatement(typeClass, RDFS.subClassOf, pokemonClass));    
      }
      
      // Add all statements from first pass
      model.add(newStatements);
      newStatements.clear();
      
      // Second pass: Process Pokemon instances
      for (Map.Entry<Resource, String> entry : typesToProcess.entrySet()) {
          Resource typeClass = entry.getKey();
          String type = entry.getValue();
          Property primaryType = model.createProperty(BASE_URI + "primaryType");
          
          // Add primary type property to type class
          newStatements.add(model.createStatement(typeClass, primaryType, type));
          
          // Find all Pokemon of this type
          StmtIterator pokemonIter = model.listStatements(null, primaryType, type);
          while (pokemonIter.hasNext()) {
              Statement stmt = pokemonIter.nextStatement();
              Resource pokemon = stmt.getSubject();
              
              // Add class memberships
              newStatements.add(model.createStatement(pokemon, RDF.type, typeClass));
              newStatements.add(model.createStatement(pokemon, RDF.type, pokemonClass));
              
              // Add subclass relationship
              Resource specificType = model.createResource(pokemon.getURI() + "/type");
              newStatements.add(model.createStatement(specificType, RDFS.subClassOf, typeClass));
              newStatements.add(model.createStatement(pokemon, RDF.type, specificType));
              newStatements.add(model.createStatement(specificType, primaryType, type));
          }
      }
      
      // Add all statements from second pass
      model.add(newStatements);
      addTransitiveSubClassClosure(model);
    }

    public static Model addInferenceRules(Model baseModel) {
      Model inferenceModel = ModelFactory.createDefaultModel();
      inferenceModel.add(baseModel);
      
      logger.info("Starting inference with {} statements", baseModel.size());
      
      // Add inference rules in specific order
      addTypeHierarchyInference(inferenceModel);
      logger.info("After type hierarchy: {} statements", inferenceModel.size());
      
      addSameAsInference(inferenceModel);
      logger.info("After sameAs inference: {} statements", inferenceModel.size());
      
      addPropertyInheritance(inferenceModel);
      logger.info("After property inheritance: {} statements", inferenceModel.size());
      
      addCharacteristicHierarchy(inferenceModel);
      logger.info("After characteristic hierarchy: {} statements", inferenceModel.size());

      // Debug statements to verify data
      debugVerifyTypes(inferenceModel);
      debugVerifySameAs(inferenceModel);
      
      return inferenceModel;
    }

    private static void debugVerifyTypes(Model model) {
      logger.info("Verifying type relationships...");
      StmtIterator typeIter = model.listStatements(null, RDF.type, (RDFNode)null);
      while (typeIter.hasNext()) {
          Statement stmt = typeIter.next();
          logger.debug("Type relationship: {} -> {}", 
              stmt.getSubject().getLocalName(),
              stmt.getObject().asResource().getLocalName());
      }
      
      StmtIterator subclassIter = model.listStatements(null, RDFS.subClassOf, (RDFNode)null);
      while (subclassIter.hasNext()) {
          Statement stmt = subclassIter.next();
          logger.debug("Subclass relationship: {} -> {}", 
              stmt.getSubject().getLocalName(),
              stmt.getObject().asResource().getLocalName());
      }
    }

    private static void debugVerifySameAs(Model model) {
      logger.info("Verifying sameAs relationships...");
      StmtIterator sameAsIter = model.listStatements(null, OWL.sameAs, (RDFNode)null);
      while (sameAsIter.hasNext()) {
          Statement stmt = sameAsIter.next();
          logger.debug("sameAs relationship: {} -> {}", 
              stmt.getSubject().getLocalName(),
              stmt.getObject().asResource().getLocalName());
          
          // Check if both resources have schema:name
          StmtIterator nameIter1 = stmt.getSubject().listProperties(
              model.createProperty(SCHEMA_URI + "name"));
          StmtIterator nameIter2 = stmt.getObject().asResource().listProperties(
              model.createProperty(SCHEMA_URI + "name"));
          
          while (nameIter1.hasNext()) {
              logger.debug("Subject name: {}", nameIter1.next().getString());
          }
          while (nameIter2.hasNext()) {
              logger.debug("Object name: {}", nameIter2.next().getString());
          }
      }
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
