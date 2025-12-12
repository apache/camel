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

package org.apache.camel.component.djl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.modality.audio.AudioFactory;
import ai.djl.modality.audio.translator.SpeechRecognitionTranslator;
import ai.djl.util.ZipUtils;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioLocalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AudioLocalTest.class);

    private static final String MODEL_URL = "https://resources.djl.ai/test-models/pytorch/wav2vec2.zip";
    private static final String MODEL_NAME = "wav2vec2.ptl";

    @BeforeAll
    public static void setupDefaultEngine() {
        // A PyTorch model
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testDJL() throws Exception {
        LOG.info("Read and load local model");
        loadLocalModel();

        LOG.info("Starting route to infer");
        context.createProducerTemplate().sendBody("controlbus:route?routeId=audio&action=start", null);
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("timer:testDJL?repeatCount=1")
                        .routeId("audio").autoStartup(false)
                        .process(exchange -> {
                            var wave = "https://resources.djl.ai/audios/speech.wav";
                            var audio = AudioFactory.newInstance().fromUrl(wave);
                            exchange.getIn().setBody(audio);
                        })
                        .to("djl:audio?model=MyModel&translator=MyTranslator")
                        .log("Result: ${body}")
                        .to("mock:result");
            }
        };
    }

    private void loadLocalModel() throws IOException, MalformedModelException, URISyntaxException {
        // Load a model
        var model = Model.newInstance(MODEL_NAME);
        // TfModel doesn't allow direct loading from remote input stream yet
        // https://github.com/deepjavalibrary/djl/issues/3303
        var modelDir = Files.createTempDirectory(MODEL_NAME);
        ZipUtils.unzip(new URI(MODEL_URL).toURL().openStream(), modelDir);
        model.load(modelDir);

        // Bind model beans
        context.getRegistry().bind("MyModel", model);
        context.getRegistry().bind("MyTranslator", new SpeechRecognitionTranslator());
    }
}
