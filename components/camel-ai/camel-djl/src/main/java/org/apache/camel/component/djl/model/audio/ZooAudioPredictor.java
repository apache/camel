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
package org.apache.camel.component.djl.model.audio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.audio.Audio;
import ai.djl.modality.audio.AudioFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.AbstractPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooAudioPredictor extends AbstractPredictor {

    private static final Logger LOG = LoggerFactory.getLogger(ZooAudioPredictor.class);

    private final ZooModel<Audio, String> model;

    public ZooAudioPredictor(DJLEndpoint endpoint) throws ModelNotFoundException, MalformedModelException, IOException {
        super(endpoint);

        Criteria.Builder<Audio, String> builder = Criteria.builder()
                .optApplication(Application.Audio.ANY)
                .setTypes(Audio.class, String.class)
                .optArtifactId(endpoint.getArtifactId());
        if (endpoint.isShowProgress()) {
            builder.optProgress(new ProgressBar());
        }

        Criteria<Audio, String> criteria = builder.build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        String result;
        if (body instanceof Audio) {
            result = predict(exchange, exchange.getIn().getBody(Audio.class));
        } else if (body instanceof byte[]) {
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            result = predict(exchange, new ByteArrayInputStream(bytes));
        } else if (body instanceof File) {
            result = predict(exchange, exchange.getIn().getBody(File.class));
        } else if (body instanceof InputStream) {
            result = predict(exchange, exchange.getIn().getBody(InputStream.class));
        } else {
            throw new RuntimeCamelException(
                    "Data type is not supported. Body should be ai.djl.modality.audio.Audio, byte[], InputStream or File");
        }
        exchange.getIn().setBody(result);
    }

    protected String predict(Exchange exchange, File input) {
        try (InputStream fileInputStream = new FileInputStream(input)) {
            Audio audio = AudioFactory.newInstance().fromInputStream(fileInputStream);
            return predict(exchange, audio);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    protected String predict(Exchange exchange, InputStream input) {
        try {
            Audio audio = AudioFactory.newInstance().fromInputStream(input);
            return predict(exchange, audio);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    protected String predict(Exchange exchange, Audio audio) {
        exchange.getIn().setHeader(DJLConstants.INPUT, audio);
        try (Predictor<Audio, String> predictor = model.newPredictor()) {
            return predictor.predict(audio);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
