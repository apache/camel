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
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the producer applies the options that describe the shape of the request and of the response.
 */
class GoogleVertexAIProducerOptionsTest {

    private static final String BASE = "google-vertexai:my-project:us-central1:gemini-2.0-flash";

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleVertexAIProducer producer(String query) throws Exception {
        context = new DefaultCamelContext();
        context.start();
        GoogleVertexAIEndpoint endpoint = context.getEndpoint(BASE + query, GoogleVertexAIEndpoint.class);
        return new GoogleVertexAIProducer(endpoint);
    }

    @Test
    void jsonModeAsksTheModelForJson() throws Exception {
        GenerateContentConfig config = producer("?jsonMode=true").buildConfig(new DefaultExchange(context));

        assertThat(config.responseMimeType()).contains("application/json");
    }

    @Test
    void jsonModeIsOffByDefault() throws Exception {
        GenerateContentConfig config = producer("").buildConfig(new DefaultExchange(context));

        assertThat(config.responseMimeType()).isEmpty();
    }

    @Test
    void theStreamOutputModeComesFromTheConfigurationOrTheHeader() throws Exception {
        GoogleVertexAIProducer producer = producer("?streamOutputMode=chunks");

        Exchange exchange = new DefaultExchange(context);
        assertThat(producer.determineStreamOutputMode(exchange)).isEqualTo("chunks");

        // the header wins over the endpoint option
        exchange.getIn().setHeader(GoogleVertexAIConstants.STREAM_OUTPUT_MODE, "complete");
        assertThat(producer.determineStreamOutputMode(exchange)).isEqualTo("complete");
    }

    @Test
    void theStreamOutputModeDefaultsToComplete() throws Exception {
        assertThat(producer("").determineStreamOutputMode(new DefaultExchange(context))).isEqualTo("complete");
    }
}
