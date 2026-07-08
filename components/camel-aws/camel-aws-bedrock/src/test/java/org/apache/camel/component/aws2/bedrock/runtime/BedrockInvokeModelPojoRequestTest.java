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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@code invoke*Model} producer operations in POJO mode (CAMEL-23462).
 * <p>
 * When {@code pojoRequest=true} but the message body is not the expected request type, the producer must fail fast with
 * a clear {@link IllegalArgumentException} — mirroring the {@code converse} and {@code applyGuardrail} operations —
 * instead of silently returning a {@code null} body. A mocked client is used so the test exercises only the producer's
 * payload-validation logic, with no AWS credentials or network access required (the exception is raised before any
 * client call).
 */
@ExtendWith(MockitoExtension.class)
public class BedrockInvokeModelPojoRequestTest {

    @Mock
    private BedrockRuntimeClient client;

    @Mock
    private BedrockRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
        registry.bind("bedrockAsyncClient", asyncClient);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                route("direct:invoke-text", "invokeTextModel");
                route("direct:invoke-image", "invokeImageModel");
                route("direct:invoke-embeddings", "invokeEmbeddingsModel");
                route("direct:invoke-text-streaming", "invokeTextModelStreaming");
                route("direct:invoke-image-streaming", "invokeImageModelStreaming");
            }

            private void route(String from, String operation) {
                from(from)
                        .to("aws-bedrock://label"
                            + "?bedrockRuntimeClient=#bedrockClient"
                            + "&bedrockRuntimeAsyncClient=#bedrockAsyncClient"
                            + "&operation=" + operation
                            + "&pojoRequest=true"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");
            }
        });
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void teardown() {
        if (template != null) {
            template.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void invokeTextModelFailsWhenPojoBodyHasWrongType() {
        assertWrongPojoBodyFails("direct:invoke-text", "not an InvokeModelRequest",
                "InvokeTextModel operation requires InvokeModelRequest in POJO mode");
    }

    @Test
    public void invokeImageModelFailsWhenPojoBodyHasWrongType() {
        assertWrongPojoBodyFails("direct:invoke-image", Map.of("prompt", "a cat"),
                "InvokeImageModel operation requires InvokeModelRequest in POJO mode");
    }

    @Test
    public void invokeEmbeddingsModelFailsWhenPojoBodyHasWrongType() {
        assertWrongPojoBodyFails("direct:invoke-embeddings", "not an InvokeModelRequest",
                "InvokeEmbeddingsModel operation requires InvokeModelRequest in POJO mode");
    }

    @Test
    public void invokeTextModelStreamingFailsWhenPojoBodyHasWrongType() {
        assertWrongPojoBodyFails("direct:invoke-text-streaming", "not a stream request",
                "Streaming invoke operations require InvokeModelWithResponseStreamRequest in POJO mode");
    }

    @Test
    public void invokeImageModelStreamingFailsWhenPojoBodyHasWrongType() {
        // invokeImageModelStreaming delegates to invokeTextModelStreaming, so it must surface the same error.
        assertWrongPojoBodyFails("direct:invoke-image-streaming", "not a stream request",
                "Streaming invoke operations require InvokeModelWithResponseStreamRequest in POJO mode");
    }

    private void assertWrongPojoBodyFails(String endpoint, Object body, String expectedMessage) {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(endpoint, body));
        Throwable cause = ex.getCause();
        assertNotNull(cause, "A wrong-type POJO body must cause the exchange to fail");
        assertInstanceOf(IllegalArgumentException.class, cause,
                "Wrong-type POJO body must raise IllegalArgumentException, was: " + cause);
        assertEquals(expectedMessage, cause.getMessage());
    }
}
