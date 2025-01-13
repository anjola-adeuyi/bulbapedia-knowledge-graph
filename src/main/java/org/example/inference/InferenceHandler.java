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

        // Add transitive closure for subClassOf
        addTransitiveSubClassClosure(inferenceModel);

        // Add transitive closure for subPropertyOf
        addTransitiveSubPropertyClosure(inferenceModel);

        // Add type inference from subClassOf
        addTypeInference(inferenceModel);

        // Add owl:sameAs symmetric and transitive closure
        addSameAsClosure(inferenceModel);

        // Add property inheritance through owl:sameAs
        addPropertyInheritance(inferenceModel);

        logger.info("Added inference rules. Original size: {}, New size: {}", 
            baseModel.size(), inferenceModel.size());
        
        return inferenceModel;
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

    private static void addTransitiveSubPropertyClosure(Model model) {
        boolean changed;
        do {
            changed = false;
            StmtIterator iter = model.listStatements(null, RDFS.subPropertyOf, (RDFNode)null);
            List<Statement> newStatements = new ArrayList<>();
            
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                if (stmt.getObject().isResource()) {
                    StmtIterator superIter = model.listStatements(
                        stmt.getObject().asResource(), RDFS.subPropertyOf, (RDFNode)null);
                    while (superIter.hasNext()) {
                        Statement superStmt = superIter.next();
                        if (!model.contains(stmt.getSubject(), RDFS.subPropertyOf, superStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                stmt.getSubject(), RDFS.subPropertyOf, superStmt.getObject()));
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

    private static void addSameAsClosure(Model model) {
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
                    StmtIterator transitiveIter = model.listStatements(
                        stmt.getObject().asResource(), OWL.sameAs, (RDFNode)null);
                    while (transitiveIter.hasNext()) {
                        Statement transitiveStmt = transitiveIter.next();
                        if (!model.contains(stmt.getSubject(), OWL.sameAs, transitiveStmt.getObject())) {
                            newStatements.add(model.createStatement(
                                stmt.getSubject(), OWL.sameAs, transitiveStmt.getObject()));
                            changed = true;
                        }
                    }
                }
            }
            model.add(newStatements);
        } while (changed);
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
