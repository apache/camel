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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The AI21 completions are Jackson 3 nodes, which a jq expression reads after a split, as in BedrockProducerIT.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockCompletionsJqTest {

    @Mock
    private BedrockRuntimeClient client;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws-bedrock://label?bedrockRuntimeClient=#bedrockClient&operation=invokeTextModel"
                            + "&modelId=ai21.j2-ultra-v1&region=us-east-1&accessKey=unused&secretKey=unused")
                        .split(body())
                        .transform().jq(".data.text", String.class)
                        .to("mock:result");
            }
        });
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void teardown() {
        template.stop();
        camelContext.stop();
    }

    @Test
    public void completionsAreReadWithJq() throws Exception {
        when(client.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(InvokeModelResponse.builder()
                        .body(SdkBytes.fromUtf8String("{\"completions\":[{\"data\":{\"text\":\"first\"}},"
                                                      + "{\"data\":{\"text\":\"second\"}}]}"))
                        .build());

        MockEndpoint result = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(List.of("first", "second"));

        template.send("direct:start", ex -> {
            ex.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            ex.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
            ex.getMessage().setBody("{}");
        });

        result.assertIsSatisfied();
    }
}
