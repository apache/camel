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

import ai.djl.modality.Classifications;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the task predictors whose Python script can return a JSON error payload surface it as an error instead
 * of treating it as a successful result, and that a result which only has the word error in it is not an error.
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

    @Test
    void textClassificationSurfacesInferenceError() {
        assertInferenceError(new TextClassificationPredictor(endpoint));
    }

    @Test
    void zeroShotClassificationSurfacesInferenceError() {
        assertInferenceError(new ZeroShotClassificationPredictor(endpoint));
    }

    @Test
    void sentenceEmbeddingsSurfacesInferenceError() {
        assertInferenceError(new SentenceEmbeddingsPredictor(endpoint));
    }

    @Test
    void automaticSpeechRecognitionSurfacesInferenceError() {
        assertInferenceError(new AutomaticSpeechRecognitionPredictor(endpoint));
    }

    @Test
    void textToSpeechSurfacesInferenceError() {
        assertInferenceError(new TextToSpeechPredictor(endpoint));
    }

    @Test
    void chatSurfacesInferenceError() {
        assertInferenceError(new ChatPredictor(endpoint));
    }

    @Test
    void textGenerationReturnsATextWithTheQuotedWordError() throws Exception {
        String text = "The parser answers with \"error\" when the input is empty";
        Exchange exchange = new DefaultExchange(context);
        new TextGenerationPredictor(endpoint).processOutput(exchange, outputWith(text));
        assertEquals(text, exchange.getMessage().getBody(String.class));
    }

    @Test
    void textGenerationReturnsAJsonObjectThatHasAnErrorFieldAndOthers() throws Exception {
        String text = "{\"error\": \"not found\", \"status\": 404}";
        Exchange exchange = new DefaultExchange(context);
        new TextGenerationPredictor(endpoint).processOutput(exchange, outputWith(text));
        assertEquals(text, exchange.getMessage().getBody(String.class));
    }

    @Test
    void textGenerationReturnsATextThatStartsWithAnErrorObject() throws Exception {
        String text = "{\"error\": \"not found\"} is what the API returns";
        Exchange exchange = new DefaultExchange(context);
        new TextGenerationPredictor(endpoint).processOutput(exchange, outputWith(text));
        assertEquals(text, exchange.getMessage().getBody(String.class));
    }

    @Test
    void summarizationReturnsATextWithTheQuotedWordError() throws Exception {
        String text = "The log has the word \"error\" twice";
        Exchange exchange = new DefaultExchange(context);
        new SummarizationPredictor(endpoint).processOutput(exchange, outputWith(text));
        assertEquals(text, exchange.getMessage().getBody(String.class));
    }

    @Test
    void questionAnsweringReturnsTheQuotedWordError() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        new QuestionAnsweringPredictor(endpoint).processOutput(exchange, outputWith("\"error\""));
        assertEquals("\"error\"", exchange.getMessage().getBody(String.class));
    }

    @Test
    void textClassificationReturnsALabelNamedError() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        new TextClassificationPredictor(endpoint).processOutput(exchange,
                outputWith("[{\"label\": \"error\", \"score\": 0.9}, {\"label\": \"ok\", \"score\": 0.1}]"));
        assertEquals("error", exchange.getMessage().getBody(Classifications.class).best().getClassName());
    }

    @Test
    void zeroShotClassificationReturnsALabelNamedError() throws Exception {
        endpoint.getConfiguration().setAutoSelect(false);
        Exchange exchange = new DefaultExchange(context);
        new ZeroShotClassificationPredictor(endpoint).processOutput(exchange,
                outputWith("{\"sequence\": \"disk full\", \"labels\": [\"error\", \"ok\"], \"scores\": [0.9, 0.1]}"));
        assertEquals("error", exchange.getMessage().getBody(Classifications.class).best().getClassName());
    }

    @Test
    void automaticSpeechRecognitionReturnsTheTranscriptError() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        new AutomaticSpeechRecognitionPredictor(endpoint).processOutput(exchange, outputWith("{\"text\": \"error\"}"));
        assertEquals("error", exchange.getMessage().getBody(String.class));
    }

    @Test
    void chatReturnsTheReplyError() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        new ChatPredictor(endpoint).processOutput(exchange, outputWith("[{\"generated_text\": ["
                                                                       + "{\"role\": \"user\", \"content\": \"Say error\"},"
                                                                       + " {\"role\": \"assistant\", \"content\": \"error\"}]}]"));
        assertEquals("error", exchange.getMessage().getBody(String.class));
    }

    @Test
    void textToImageReturnsImageBytesThatHaveTheQuotedWordError() throws Exception {
        // the PNG signature, then bytes that read as the quoted word error
        byte[] image = { (byte) 0x89, 'P', 'N', 'G', ' ', '"', 'e', 'r', 'r', 'o', 'r', '"' };
        Output output = new Output();
        output.add("data", image);
        Exchange exchange = new DefaultExchange(context);
        new TextToImagePredictor(endpoint).processOutput(exchange, output);
        assertArrayEquals(image, exchange.getMessage().getBody(byte[].class));
    }

    @Test
    void textGenerationSurfacesAnErrorPayloadWithSpaces() {
        TextGenerationPredictor predictor = new TextGenerationPredictor(endpoint);
        Exchange exchange = new DefaultExchange(context);
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> predictor.processOutput(exchange, outputWith("{ \"error\" : \"model failed to load\" }")));
        assertEquals("Python inference failed: model failed to load", e.getMessage());
    }

    @Test
    void textGenerationFailsWithoutOutput() {
        assertNoOutputFails(new TextGenerationPredictor(endpoint));
    }

    @Test
    void summarizationFailsWithoutOutput() {
        assertNoOutputFails(new SummarizationPredictor(endpoint));
    }

    @Test
    void questionAnsweringFailsWithoutOutput() {
        assertNoOutputFails(new QuestionAnsweringPredictor(endpoint));
    }

    @Test
    void zeroShotClassificationFailsWithoutOutput() {
        assertNoOutputFails(new ZeroShotClassificationPredictor(endpoint));
    }

    @Test
    void textToImageFailsWithoutOutput() {
        assertNoOutputFails(new TextToImagePredictor(endpoint));
    }

    private void assertNoOutputFails(AbstractTaskPredictor predictor) {
        Exchange exchange = new DefaultExchange(context);
        // an output without the data entry, which DJL reads as null
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> predictor.processOutput(exchange, new Output()));
        assertEquals("Python inference returned no output", e.getMessage());
    }

    private void assertInferenceError(AbstractTaskPredictor predictor) {
        Exchange exchange = new DefaultExchange(context);
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> predictor.processOutput(exchange, outputWith(ERROR_PAYLOAD)));
        assertTrue(e.getMessage().contains("model failed to load"), e.getMessage());
    }
}
