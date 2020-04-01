package org.apache.camel.component.djl.model;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.ImageVisualization;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZooObjectDetectionPredictor extends AbstractPredictor {

    private static final Logger LOG = LoggerFactory.getLogger(ZooObjectDetectionPredictor.class);

    private ZooModel<BufferedImage, DetectedObjects> model;

    public ZooObjectDetectionPredictor(String artifactId) throws Exception {
        Criteria<BufferedImage, DetectedObjects> criteria =
                Criteria.builder()
                        .optApplication(Application.CV.OBJECT_DETECTION)
                        .setTypes(BufferedImage.class, DetectedObjects.class)
                        .optArtifactId(artifactId)
                        .optProgress(new ProgressBar())
                        .build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getBody() instanceof byte[]){
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            DetectedObjects result = classify(new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File){
            DetectedObjects result = classify(exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream){
            DetectedObjects result = classify(exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        }
    }

    public DetectedObjects classify(BufferedImage input) throws Exception {
        try (Predictor<BufferedImage, DetectedObjects> predictor = model.newPredictor()) {
            DetectedObjects detectedObjects = predictor.predict(input);
            return detectedObjects;
        } catch (TranslateException e) {
            throw new Exception("Failed to process output", e);
        }
    }

    public DetectedObjects classify(File input) throws Exception {
        try {
            return classify(ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    public DetectedObjects classify(InputStream input) throws Exception {
        try {
            return classify(ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }
}
