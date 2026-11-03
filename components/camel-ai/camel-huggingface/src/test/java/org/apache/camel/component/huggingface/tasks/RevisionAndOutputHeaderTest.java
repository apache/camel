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
import ai.djl.modality.audio.Audio;
import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sentence-embeddings and text-to-image tasks must honour the configured model revision (they were the only two
 * that dropped it), and the text-to-image task must publish its result on the OUTPUT header as its Javadoc promises.
 * The text-to-speech task gives a DJL Audio, which is not encoded audio bytes, so it must not claim a Content-Type.
 */
class RevisionAndOutputHeaderTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private HuggingFaceEndpoint endpoint(HuggingFaceConfiguration config) {
        HuggingFaceEndpoint endpoint = new HuggingFaceEndpoint(null, null, config);
        endpoint.setCamelContext(context);
        return endpoint;
    }

    @Test
    void sentenceEmbeddingsScriptPinsRevision() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("sentence-transformers/all-MiniLM-L6-v2");
        config.setRevision("v1.5");
        SentenceEmbeddingsPredictor predictor = new SentenceEmbeddingsPredictor(endpoint(config));
        assertTrue(predictor.getPythonScript().contains("revision='v1.5'"),
                "the generated script must pin the configured revision");
    }

    @Test
    void textToImageScriptPinsRevision() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("stabilityai/stable-diffusion");
        config.setRevision("fp16");
        TextToImagePredictor predictor = new TextToImagePredictor(endpoint(config));
        assertTrue(predictor.getPythonScript().contains("revision='fp16'"),
                "the generated script must pin the configured revision");
    }

    @Test
    void textToImagePublishesTheImageOnTheOutputHeader() throws Exception {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        TextToImagePredictor predictor = new TextToImagePredictor(endpoint(config));
        Exchange exchange = new DefaultExchange(context);
        Output output = new Output();
        byte[] image = { 1, 2, 3, 4 };
        output.add("data", image);

        predictor.processOutput(exchange, output);

        assertArrayEquals(image, exchange.getMessage().getHeader(HuggingFaceConstants.OUTPUT, byte[].class));
    }

    @Test
    void textToSpeechGivesTheAudioWithoutAContentType() throws Exception {
        TextToSpeechPredictor predictor = new TextToSpeechPredictor(endpoint(new HuggingFaceConfiguration()));
        Exchange exchange = new DefaultExchange(context);
        // the request, for example from platform-http, has the content type of its text
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
        exchange.getIn().setBody("Hello world");
        Output output = new Output();
        output.add("data", "{\"audio\": [0.5, -0.5, 0.25], \"sampling_rate\": 16000}");

        predictor.processOutput(exchange, output);

        Audio audio = exchange.getMessage().getBody(Audio.class);
        assertArrayEquals(new float[] { 0.5f, -0.5f, 0.25f }, audio.getData());
        assertEquals(16000, audio.getSampleRate());
        assertEquals(1, audio.getChannels());
        assertSame(audio, exchange.getMessage().getHeader(HuggingFaceConstants.OUTPUT));
        assertNull(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE),
                "the body is a DJL Audio, not encoded audio bytes, so the result must have no Content-Type");
    }
}
