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

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.qa.QAInput;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.AbstractPredictor;

public class CustomQuestionAnswerPredictor extends AbstractPredictor {

    private final String modelName;
    private final String translatorName;

    public CustomQuestionAnswerPredictor(DJLEndpoint endpoint) {
        super(endpoint);
        this.modelName = endpoint.getModel();
        this.translatorName = endpoint.getTranslator();
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
        Model model = exchange.getContext().getRegistry().lookupByNameAndType(modelName, Model.class);
        @SuppressWarnings("unchecked")
        Translator<QAInput, String> translator =
                exchange.getContext().getRegistry().lookupByNameAndType(translatorName, Translator.class);

        exchange.getIn().setHeader(DJLConstants.INPUT, input);
        try (Predictor<QAInput, String> predictor = model.newPredictor(translator)) {
            return predictor.predict(input);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
