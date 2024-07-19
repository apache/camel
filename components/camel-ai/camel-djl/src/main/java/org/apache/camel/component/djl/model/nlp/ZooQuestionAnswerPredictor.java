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
package org.apache.camel.component.djl.model.nlp;

import java.io.IOException;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.qa.QAInput;
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

public class ZooQuestionAnswerPredictor extends AbstractPredictor {

    private final ZooModel<QAInput, String> model;

    public ZooQuestionAnswerPredictor(DJLEndpoint endpoint) throws ModelNotFoundException, MalformedModelException,
                                                            IOException {
        super(endpoint);

        Criteria.Builder<QAInput, String> builder = Criteria.builder()
                .optApplication(Application.NLP.QUESTION_ANSWER)
                .setTypes(QAInput.class, String.class)
                .optArtifactId(endpoint.getArtifactId());
        if (endpoint.isShowProgress()) {
            builder.optProgress(new ProgressBar());
        }

        Criteria<QAInput, String> criteria = builder.build();
        this.model = ModelZoo.loadModel(criteria);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        String result;
        if (body instanceof QAInput) {
            QAInput input = exchange.getIn().getBody(QAInput.class);
            result = predict(exchange, input);
        } else if (body instanceof String[]) {
            String[] strs = exchange.getIn().getBody(String[].class);
            if (strs.length < 2) {
                throw new RuntimeCamelException("Input String[] should have two elements");
            }
            QAInput input = new QAInput(strs[0], strs[1]);
            result = predict(exchange, input);
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be String[] or QAInput");
        }
        exchange.getIn().setBody(result);
    }

    protected String predict(Exchange exchange, QAInput input) {
        exchange.getIn().setHeader(DJLConstants.INPUT, input);
        try (Predictor<QAInput, String> predictor = model.newPredictor()) {
            return predictor.predict(input);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
