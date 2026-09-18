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
package org.apache.camel.component.huggingface.tasks;

import ai.djl.modality.Output;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the task predictors whose Python script can return a JSON error payload surface it as an error instead
 * of treating it as a successful result.
 */
class PredictorErrorHandlingTest {

    private static final String ERROR_PAYLOAD = "{\"error\": \"model failed to load\"}";

    private CamelContext context;
    private HuggingFaceEndpoint endpoint;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        endpoint = new HuggingFaceEndpoint(null, null, new HuggingFaceConfiguration());
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Output outputWith(String data) {
        Output output = new Output();
        output.add("data", data);
        return output;
    }

    @Test
    void textGenerationSurfacesInferenceError() {
        TextGenerationPredictor predictor = new TextGenerationPredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        assertThrows(RuntimeCamelException.class, () -> predictor.processOutput(exchange, outputWith(ERROR_PAYLOAD)));
    }

    @Test
    void textGenerationPassesThroughSuccessfulResult() throws Exception {
        TextGenerationPredictor predictor = new TextGenerationPredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        predictor.processOutput(exchange, outputWith("a generated sentence"));
        assertEquals("a generated sentence", exchange.getMessage().getBody(String.class));
    }

    @Test
    void summarizationSurfacesInferenceError() {
        SummarizationPredictor predictor = new SummarizationPredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        assertThrows(RuntimeCamelException.class, () -> predictor.processOutput(exchange, outputWith(ERROR_PAYLOAD)));
    }

    @Test
    void questionAnsweringSurfacesInferenceError() {
        QuestionAnsweringPredictor predictor = new QuestionAnsweringPredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        assertThrows(RuntimeCamelException.class, () -> predictor.processOutput(exchange, outputWith(ERROR_PAYLOAD)));
    }

    @Test
    void textToImageSurfacesInferenceError() {
        TextToImagePredictor predictor = new TextToImagePredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        assertThrows(RuntimeCamelException.class, () -> predictor.processOutput(exchange, outputWith(ERROR_PAYLOAD)));
    }
}
