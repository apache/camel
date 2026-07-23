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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for the {@code applyGuardrail} producer operation. Uses a mocked {@link BedrockRuntimeClient} so the test
 * exercises only the producer's request-building logic — no AWS credentials or network access required.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockApplyGuardrailTest {

    @Mock
    private BedrockRuntimeClient client;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<ApplyGuardrailRequest> capturedRequest;

    @BeforeEach
    public void setup() throws Exception {
        capturedRequest = new AtomicReference<>();
        lenient().doAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0, ApplyGuardrailRequest.class));
            return ApplyGuardrailResponse.builder().action(GuardrailAction.NONE).build();
        }).when(client).applyGuardrail(any(ApplyGuardrailRequest.class));

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:apply-guardrail-config")
                        .to("aws-bedrock://label"
                            + "?bedrockRuntimeClient=#bedrockClient"
                            + "&operation=applyGuardrail"
                            + "&guardrailIdentifier=endpoint-guardrail-id"
                            + "&guardrailVersion=1"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");

                from("direct:apply-guardrail-no-config")
                        .to("aws-bedrock://label2"
                            + "?bedrockRuntimeClient=#bedrockClient"
                            + "&operation=applyGuardrail"
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
    public void applyGuardrailUsesEndpointGuardrailIdentifierWhenNoHeader() {
        template.send("direct:apply-guardrail-config",
                exchange -> exchange.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONTENT, sampleContent()));

        ApplyGuardrailRequest request = capturedRequest.get();
        assertNotNull(request, "Producer must have invoked the bedrock client");
        assertEquals("endpoint-guardrail-id", request.guardrailIdentifier(),
                "Identifier should fall back to the endpoint configuration when no header is set");
    }

    @Test
    public void applyGuardrailUsesGuardrailIdentifierHeaderWhenSet() {
        template.send("direct:apply-guardrail-config", exchange -> {
            exchange.getMessage().setHeader(BedrockConstants.GUARDRAIL_IDENTIFIER, "header-guardrail-id");
            exchange.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONTENT, sampleContent());
        });

        ApplyGuardrailRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("header-guardrail-id", request.guardrailIdentifier(),
                "Identifier should be read from the CamelAwsBedrockGuardrailIdentifier header");
    }

    @Test
    public void applyGuardrailDoesNotReadIdentifierFromGuardrailConfigHeader() {
        // GUARDRAIL_CONFIG carries a GuardrailConfiguration (used by converse), not a String identifier.
        // Setting it must not corrupt the applyGuardrail request — the endpoint identifier must still be used.
        template.send("direct:apply-guardrail-config", exchange -> {
            GuardrailConfiguration converseConfig = GuardrailConfiguration.builder()
                    .guardrailIdentifier("converse-guardrail-id")
                    .guardrailVersion("DRAFT")
                    .build();
            exchange.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONFIG, converseConfig);
            exchange.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONTENT, sampleContent());
        });

        ApplyGuardrailRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("endpoint-guardrail-id", request.guardrailIdentifier(),
                "applyGuardrail must not consume the GuardrailConfiguration from CamelAwsBedrockGuardrailConfig");
    }

    @Test
    public void applyGuardrailFailsWhenGuardrailIdentifierIsMissing() {
        Exchange exchange = template.send("direct:apply-guardrail-no-config",
                ex -> ex.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONTENT, sampleContent()));
        Throwable cause = exchange.getException();
        assertNotNull(cause, "Producer should fail when no guardrailIdentifier is provided");
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("guardrailIdentifier"),
                "Error message should mention guardrailIdentifier — was: " + cause.getMessage());
    }

    private static List<GuardrailContentBlock> sampleContent() {
        List<GuardrailContentBlock> content = new ArrayList<>();
        content.add(GuardrailContentBlock.builder()
                .text(GuardrailTextBlock.builder().text("test").build())
                .build());
        return content;
    }
}
