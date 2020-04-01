package org.apache.camel.component.djl.model;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomImageClassificationPredictor extends AbstractPredictor {
    private static final Logger LOG = LoggerFactory.getLogger(CustomImageClassificationPredictor.class);

    private String modelName;
    private String translatorName;

    public CustomImageClassificationPredictor(String modelName, String translatorName) {
        this.modelName = modelName;
        this.translatorName = translatorName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Model model = exchange.getContext().getRegistry().lookupByNameAndType(modelName, Model.class);
        Translator translator = exchange.getContext().getRegistry().lookupByNameAndType(translatorName, Translator.class);

        if (exchange.getIn().getBody() instanceof byte[]) {
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            Map<String, Float> result = classify(model, translator, new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File) {
            Map<String, Float> result = classify(model, translator, exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream) {
            Map<String, Float> result = classify(model, translator, exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, File input) throws Exception {
        try {
            return classify(model, translator, ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, InputStream input) throws Exception {
        try {
            return classify(model, translator, ImageIO.read(input));
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new Exception("Couldn't transform input into a BufferedImage", e);
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, BufferedImage input) throws Exception {
        try (Predictor<BufferedImage, Classifications> predictor = model.newPredictor(translator)) {
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
