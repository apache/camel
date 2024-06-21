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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
            Classifications result = classify(new ByteArrayInputStream(bytes));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof File) {
            Classifications result = classify(exchange.getIn().getBody(File.class));
            exchange.getIn().setBody(result);
        } else if (exchange.getIn().getBody() instanceof InputStream) {
            Classifications result = classify(exchange.getIn().getBody(InputStream.class));
            exchange.getIn().setBody(result);
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be byte[], InputStream or File");
        }
    }

    public Classifications classify(File input) {
        try (InputStream fileInputStream = new FileInputStream(input)) {
            Image image = ImageFactory.getInstance().fromInputStream(fileInputStream);
            return classify(image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    public Classifications classify(InputStream input) {
        try {
            Image image = ImageFactory.getInstance().fromInputStream(input);
            return classify(image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    public Classifications classify(Image image) {
        try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
            return predictor.predict(image);
        } catch (TranslateException e) {
            LOG.error("Could not process input or output", e);
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
