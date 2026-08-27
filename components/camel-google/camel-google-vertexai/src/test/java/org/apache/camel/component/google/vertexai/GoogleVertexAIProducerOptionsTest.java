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
package org.apache.camel.component.google.vertexai;

import com.google.genai.types.GenerateContentConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the producer applies the options that describe the shape of the request and of the response. Uses
 * direct object construction to avoid starting the endpoint (which requires Google Cloud credentials).
 */
class GoogleVertexAIProducerOptionsTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleVertexAIProducer producer(GoogleVertexAIConfiguration config) {
        context = new DefaultCamelContext();
        GoogleVertexAIComponent component = new GoogleVertexAIComponent(context);
        GoogleVertexAIEndpoint endpoint
                = new GoogleVertexAIEndpoint("google-vertexai:my-project:us-central1:gemini-2.0-flash", component, config);
        return new GoogleVertexAIProducer(endpoint);
    }

    @Test
    void jsonModeAsksTheModelForJson() {
        GoogleVertexAIConfiguration config = new GoogleVertexAIConfiguration();
        config.setJsonMode(true);
        GenerateContentConfig result = producer(config).buildConfig(new DefaultExchange(context));

        assertThat(result.responseMimeType()).contains("application/json");
    }

    @Test
    void jsonModeIsOffByDefault() {
        GoogleVertexAIConfiguration config = new GoogleVertexAIConfiguration();
        GenerateContentConfig result = producer(config).buildConfig(new DefaultExchange(context));

        assertThat(result.responseMimeType()).isEmpty();
    }
}
