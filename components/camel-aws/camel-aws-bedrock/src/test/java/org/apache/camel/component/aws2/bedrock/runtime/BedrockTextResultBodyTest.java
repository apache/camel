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
package org.apache.camel.component.aws2.bedrock.runtime;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The text result of a model is a String body, so a route reads it as text, bytes or a stream without camel-jackson3.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockTextResultBodyTest {

    @Mock
    private BedrockRuntimeClient client;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
        camelContext = new DefaultCamelContext(registry);
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void teardown() {
        template.stop();
        camelContext.stop();
    }

    @ParameterizedTest
    @CsvSource({
            "meta.llama3-8b-instruct-v1:0, generation",
            "cohere.command-r-v1:0, text",
            "anthropic.claude-v2, completion" })
    public void textResultIsAString(String modelId, String field) {
        when(client.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(InvokeModelResponse.builder()
                        .body(SdkBytes.fromUtf8String("{\"" + field + "\":\"hello \\\"world\\\"\"}")).build());

        Exchange exchange = template.send("aws-bedrock://label?bedrockRuntimeClient=#bedrockClient"
                                          + "&operation=invokeTextModel&modelId=" + modelId
                                          + "&region=us-east-1&accessKey=unused&secretKey=unused",
                ex -> {
                    ex.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
                    ex.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
                    ex.getMessage().setBody("{}");
                });

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("hello \"world\"");
        assertThat(exchange.getMessage().getBody(byte[].class)).isEqualTo("hello \"world\"".getBytes());
        assertThat(exchange.getMessage().getBody(InputStream.class)).isNotNull();
    }
}
