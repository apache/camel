package org.apache.camel.component.djl.model;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class CustomObjectDetectionPredictor extends AbstractPredictor {

    private static final Logger LOG = LoggerFactory.getLogger(CustomObjectDetectionPredictor.class);

    private String modelName;
    private String translatorName;

    public CustomObjectDetectionPredictor(String modelName, String translatorName) {
        this.modelName = modelName;
        this.translatorName = translatorName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Model model = exchange.getContext().getRegistry().lookupByNameAndType(modelName, Model.class);
        Translator translator = exchange.getContext().getRegistry().lookupByNameAndType(translatorName, Translator.class);

        if (exchange.getIn().getBody() instanceof byte[]){
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            DetectedObjects result = classify(model, translator, new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File){
            DetectedObjects result = classify(model, translator, exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream){
            DetectedObjects result = classify(model, translator, exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        }
    }

    public DetectedObjects classify(Model model, Translator translator,BufferedImage input) throws Exception {
        try (Predictor<BufferedImage, DetectedObjects> predictor = model.newPredictor(translator)) {
            DetectedObjects detectedObjects = predictor.predict(input);
            return detectedObjects;
        } catch (TranslateException e) {
            throw new Exception("Failed to process output", e);
        }
    }

    public DetectedObjects classify(Model model, Translator translator,File input) throws Exception {
        try {
            return classify(model, translator, ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    public DetectedObjects classify(Model model, Translator translator,InputStream input) throws Exception {
        try {
            return classify(model, translator, ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }
}
