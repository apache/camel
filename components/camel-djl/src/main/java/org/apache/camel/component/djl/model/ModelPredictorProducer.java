package org.apache.camel.component.djl.model;

import static ai.djl.Application.CV.IMAGE_CLASSIFICATION;
import static ai.djl.Application.CV.OBJECT_DETECTION;

public class ModelPredictorProducer {

    public static AbstractPredictor getZooPredictor(String applicationPath, String artifactId) throws Exception {
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())){
            return new ZooImageClassificationPredictor(artifactId);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())){
            return new ZooObjectDetectionPredictor(artifactId);
        } else {
            throw new RuntimeException("Application not supported ");
        }
    }

    public static AbstractPredictor getCustomPredictor(String applicationPath, String model, String translator) {
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())){
            return new CustomImageClassificationPredictor(model, translator);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())){
            return new CustomObjectDetectionPredictor(model, translator);
        } else {
            throw new RuntimeException("Application not supported ");
        }
    }
}
