package org.example.validation;

import java.io.FileInputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RDFValidator {
    private static final Logger logger = LoggerFactory.getLogger(RDFValidator.class);
    private final Model shapesModel;

    public RDFValidator(Model shapesModel) {
        this.shapesModel = shapesModel;
    }

    public boolean validate(Model dataModel) {
        Shapes shapes = Shapes.parse(shapesModel);
        ValidationReport report = ShaclValidator.get().validate(shapes, dataModel.getGraph());
        
        if (report.conforms()) {
            logger.info("Data model conforms to SHACL shapes!");
            return true;
        } else {
            logger.warn("Data model does not conform to SHACL shapes:");
            ShLib.printReport(report);
            return false;
        }
    }

    public Model getShapesModel() {
        return shapesModel;
    }

    public static ValidationResult validateRDF(String filename) {
        try {
            Model model = ModelFactory.createDefaultModel();
            model.read(new FileInputStream(filename), null, "TURTLE");
            
            ValidationResult result = new ValidationResult();
            result.setValid(true);
            result.setTripleCount(model.size());
            result.setMessage("RDF is valid. Total triples: " + model.size());
            
            return result;
        } catch (Exception e) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setMessage("RDF validation error: " + e.getMessage());
            return result;
        }
    }
    
    public static class ValidationResult {
        private boolean valid;
        private long tripleCount;
        private String message;
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public long getTripleCount() { return tripleCount; }
        public void setTripleCount(long tripleCount) { this.tripleCount = tripleCount; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
