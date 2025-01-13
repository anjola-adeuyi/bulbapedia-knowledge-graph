package org.example.inference;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(InferenceHandler.class);

    public static Model addInferenceRules(Model baseModel) {
        // Create an inference model based on RDFS rules
        Model inferenceModel = ModelFactory.createRDFSModel(baseModel);
        
        // Add owl:sameAs inference
        addSameAsInference(inferenceModel);
        
        // Add type hierarchy inference
        addTypeHierarchyInference(inferenceModel);
        
        // Add property inheritance
        addPropertyInheritance(inferenceModel);

        logger.info("Added inference rules to model. Original size: {}, New size: {}", 
            baseModel.size(), inferenceModel.size());
            
        return inferenceModel;
    }

    private static void addSameAsInference(Model model) {
        // Find all owl:sameAs statements and add inverse relations
        StmtIterator sameAsStmts = model.listStatements(null, OWL.sameAs, (RDFNode)null);
        while (sameAsStmts.hasNext()) {
            Statement stmt = sameAsStmts.next();
            if (stmt.getObject().isResource()) {
                // Add inverse sameAs
                model.add(stmt.getObject().asResource(), OWL.sameAs, stmt.getSubject());
                
                // Add property sharing in both directions
                addSharedProperties(model, stmt.getSubject(), stmt.getObject().asResource());
                addSharedProperties(model, stmt.getObject().asResource(), stmt.getSubject());
            }
        }
    }

    private static void addSharedProperties(Model model, Resource r1, Resource r2) {
        // Share all properties between resources that are owl:sameAs
        StmtIterator props = model.listStatements(r1, null, (RDFNode)null);
        while (props.hasNext()) {
            Statement stmt = props.next();
            if (!stmt.getPredicate().equals(OWL.sameAs)) {
                model.add(r2, stmt.getPredicate(), stmt.getObject());
            }
        }
    }

    private static void addTypeHierarchyInference(Model model) {
        // Add RDFS subclass inference
        StmtIterator subclassStmts = model.listStatements(null, RDFS.subClassOf, (RDFNode)null);
        while (subclassStmts.hasNext()) {
            Statement stmt = subclassStmts.next();
            if (stmt.getObject().isResource()) {
                Resource subClass = stmt.getSubject();
                Resource superClass = stmt.getObject().asResource();
                
                // Inherit types from superclass
                StmtIterator instances = model.listStatements(null, RDF.type, subClass);
                while (instances.hasNext()) {
                    Resource instance = instances.next().getSubject();
                    model.add(instance, RDF.type, superClass);
                }
            }
        }
    }

    private static void addPropertyInheritance(Model model) {
        // Add RDFS property inheritance
        StmtIterator subPropStmts = model.listStatements(null, RDFS.subPropertyOf, (RDFNode)null);
        while (subPropStmts.hasNext()) {
            Statement stmt = subPropStmts.next();
            if (stmt.getObject().isResource()) {
                Property subProp = stmt.getSubject().as(Property.class);
                Property superProp = stmt.getObject().as(Property.class);
                
                // Inherit property values
                StmtIterator propInstances = model.listStatements(null, subProp, (RDFNode)null);
                while (propInstances.hasNext()) {
                    Statement propStmt = propInstances.next();
                    model.add(propStmt.getSubject(), superProp, propStmt.getObject());
                }
            }
        }
    }
}
