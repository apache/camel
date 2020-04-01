package org.apache.camel.component.djl.model;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ZooImageClassificationPredictor extends AbstractPredictor {
    private static final Logger LOG = LoggerFactory.getLogger(ZooImageClassificationPredictor.class);

    private ZooModel<BufferedImage, Classifications> model;

    public ZooImageClassificationPredictor(String artifactId) throws Exception {
        Criteria<BufferedImage, Classifications> criteria =
                Criteria.builder()
                        .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                        .setTypes(BufferedImage.class, Classifications.class)
                        .optArtifactId(artifactId)
                        .optProgress(new ProgressBar())
                        .build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getBody() instanceof byte[]){
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            Map<String, Float> result = classify(new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File){
            Map<String, Float> result = classify(exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream){
            Map<String, Float> result = classify(exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        }
    }

    public Map<String, Float> classify(File input) throws Exception {
        try {
            return classify(ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    public Map<String, Float> classify(InputStream input) throws Exception {
        try {
            return classify(ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    public Map<String, Float> classify(BufferedImage input) throws Exception {
        try (Predictor<BufferedImage, Classifications> predictor = model.newPredictor()) {
            Classifications classifications = predictor.predict(input);
            List<Classifications.Classification> list = classifications.items();
            return list.stream()
                    .collect(
                            Collectors.toMap(
                                    Classifications.Classification::getClassName,
                                    x -> (float) x.getProbability()));
        } catch (TranslateException e) {
            throw new Exception("Failed to process output", e);
        }
    }
}
