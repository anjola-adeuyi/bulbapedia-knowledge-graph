package org.example.inference;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(InferenceHandler.class);

    public static Model addInferenceRules(Model baseModel) {
        // Create a new model for inference
        Model inferenceModel = ModelFactory.createDefaultModel();
        inferenceModel.add(baseModel);

        // Add schema.org class hierarchy
        addTypeHierarchy(inferenceModel);

        // Add transitive properties
        addTransitiveProperties(inferenceModel);

        logger.info("Added inference rules to model. Original size: {}, New size: {}", 
            baseModel.size(), inferenceModel.size());
            
        return inferenceModel;
    }

    private static void addTypeHierarchy(Model model) {
        // Collect all statements first
        List<Statement> newStatements = new ArrayList<>();
        
        // Find all rdf:type statements
        StmtIterator typeStmts = model.listStatements(null, RDF.type, (RDFNode)null);
        while (typeStmts.hasNext()) {
            Statement stmt = typeStmts.next();
            if (stmt.getObject().isResource()) {
                Resource type = stmt.getObject().asResource();
                // Get all superclasses
                StmtIterator superClassStmts = model.listStatements(type, RDFS.subClassOf, (RDFNode)null);
                while (superClassStmts.hasNext()) {
                    Statement superClassStmt = superClassStmts.next();
                    if (superClassStmt.getObject().isResource()) {
                        // Add instance-of relationship to superclass
                        newStatements.add(
                            model.createStatement(
                                stmt.getSubject(), 
                                RDF.type, 
                                superClassStmt.getObject()
                            )
                        );
                    }
                }
            }
        }
        
        // Add all collected statements
        model.add(newStatements);
    }

    private static void addTransitiveProperties(Model model) {
        // Handle owl:sameAs transitivity
        List<Statement> newSameAsStmts = new ArrayList<>();
        StmtIterator sameAsStmts = model.listStatements(null, OWL.sameAs, (RDFNode)null);
        
        while (sameAsStmts.hasNext()) {
            Statement stmt = sameAsStmts.next();
            if (stmt.getObject().isResource()) {
                Resource subject = stmt.getSubject();
                Resource object = stmt.getObject().asResource();
                
                // Add inverse sameAs
                newSameAsStmts.add(model.createStatement(object, OWL.sameAs, subject));
                
                // Add property sharing in both directions
                addPropertySharing(model, subject, object, newSameAsStmts);
                addPropertySharing(model, object, subject, newSameAsStmts);
            }
        }
        
        // Add all collected statements
        model.add(newSameAsStmts);
        
        // Handle rdfs:subPropertyOf transitivity
        List<Statement> newPropertyStmts = new ArrayList<>();
        StmtIterator subPropStmts = model.listStatements(null, RDFS.subPropertyOf, (RDFNode)null);
        
        while (subPropStmts.hasNext()) {
            Statement stmt = subPropStmts.next();
            if (stmt.getObject().isResource()) {
                Property subProp = stmt.getSubject().as(Property.class);
                Property superProp = stmt.getObject().asResource().as(Property.class);
                
                StmtIterator instances = model.listStatements(null, subProp, (RDFNode)null);
                while (instances.hasNext()) {
                    Statement instance = instances.next();
                    newPropertyStmts.add(
                        model.createStatement(
                            instance.getSubject(),
                            superProp,
                            instance.getObject()
                        )
                    );
                }
            }
        }
        
        // Add all collected statements
        model.add(newPropertyStmts);
    }

    private static void addPropertySharing(Model model, Resource r1, Resource r2, List<Statement> newStatements) {
        StmtIterator props = model.listStatements(r1, null, (RDFNode)null);
        while (props.hasNext()) {
            Statement stmt = props.next();
            if (!stmt.getPredicate().equals(OWL.sameAs)) {
                newStatements.add(
                    model.createStatement(r2, stmt.getPredicate(), stmt.getObject())
                );
            }
        }
    }
}
