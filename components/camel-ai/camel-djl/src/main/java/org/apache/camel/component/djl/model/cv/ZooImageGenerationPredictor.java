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

package org.apache.camel.component.djl.model.cv;

import java.io.IOException;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
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

public class ZooImageGenerationPredictor extends AbstractPredictor {

    protected ZooModel<int[], Image[]> model;

    public ZooImageGenerationPredictor(DJLEndpoint endpoint)
            throws ModelNotFoundException, MalformedModelException, IOException {
        super(endpoint);

        Criteria.Builder<int[], Image[]> builder = Criteria.builder()
                .optApplication(Application.CV.IMAGE_GENERATION)
                .setTypes(int[].class, Image[].class)
                .optArtifactId(endpoint.getArtifactId());
        if (endpoint.isShowProgress()) {
            builder.optProgress(new ProgressBar());
        }

        Criteria<int[], Image[]> criteria = builder.build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) {
        if (exchange.getIn().getBody() instanceof int[]) {
            int[] seed = exchange.getIn().getBody(int[].class);
            Image[] result = predict(exchange, seed);
            exchange.getIn().setBody(result);
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be int[]");
        }
    }

    protected Image[] predict(Exchange exchange, int[] seed) {
        exchange.getIn().setHeader(DJLConstants.INPUT, seed);
        try (Predictor<int[], Image[]> predictor = model.newPredictor()) {
            return predictor.predict(seed);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
