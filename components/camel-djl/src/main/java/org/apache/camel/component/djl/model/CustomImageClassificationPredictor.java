/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.djl.model;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomImageClassificationPredictor extends AbstractPredictor {
    private static final Logger LOG = LoggerFactory.getLogger(CustomImageClassificationPredictor.class);

    private final String modelName;
    private final String translatorName;

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
        } else {
            throw new RuntimeException("Data type is not supported. Body should be byte[], InputStream or File");
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, File input) throws Exception {
        try {
            Image image = ImageFactory.getInstance().fromInputStream(new FileInputStream(input));
            return classify(model, translator, image);
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new RuntimeException("Couldn't transform input into a BufferedImage", e);
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, InputStream input) throws Exception {
        try {
            Image image = ImageFactory.getInstance().fromInputStream(input);
            return classify(model, translator, image);
        } catch (IOException e) {
            LOG.error("Couldn't transform input into a BufferedImage");
            throw new RuntimeException("Couldn't transform input into a BufferedImage", e);
        }
    }

    private Map<String, Float> classify(Model model, Translator translator, Image image) throws Exception {
        try (Predictor<Image, Classifications> predictor = model.newPredictor(translator)) {
            Classifications classifications = predictor.predict(image);
            List<Classifications.Classification> list = classifications.items();
            return list.stream()
                    .collect(Collectors.toMap(Classifications.Classification::getClassName, x -> (float) x.getProbability()));
        } catch (TranslateException e) {
            LOG.error("Could not process input or output", e);
            throw new RuntimeException("Could not process input or output", e);
        }
    }
}
