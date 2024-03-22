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

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooImageClassificationPredictor extends AbstractPredictor {
    private static final Logger LOG = LoggerFactory.getLogger(ZooImageClassificationPredictor.class);

    private final ZooModel<Image, Classifications> model;

    public ZooImageClassificationPredictor(String artifactId) throws ModelNotFoundException, MalformedModelException,
                                                              IOException {
        Criteria<Image, Classifications> criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, Classifications.class)
                .optArtifactId(artifactId)
                .optProgress(new ProgressBar())
                .build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) {
        if (exchange.getIn().getBody() instanceof byte[]) {
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            Map<String, Float> result = classify(new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File) {
            Map<String, Float> result = classify(exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream) {
            Map<String, Float> result = classify(exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be byte[], InputStream or File");
        }
    }

    public Map<String, Float> classify(File input) {
        try (InputStream fileInputStream = new FileInputStream(input)) {
            Image image = ImageFactory.getInstance().fromInputStream(fileInputStream);
            return classify(image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    public Map<String, Float> classify(InputStream input) {
        try {
            Image image = ImageFactory.getInstance().fromInputStream(input);
            return classify(image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    public Map<String, Float> classify(Image image) {
        try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
            Classifications classifications = predictor.predict(image);
            List<Classifications.Classification> list = classifications.items();
            return list.stream()
                    .collect(Collectors.toMap(Classifications.Classification::getClassName, x -> (float) x.getProbability()));
        } catch (TranslateException e) {
            LOG.error("Could not process input or output", e);
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
