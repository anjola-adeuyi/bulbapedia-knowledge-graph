package org.example.validation;

import org.apache.jena.rdf.model.Model;
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
}
