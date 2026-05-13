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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
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
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class BedrockAgentRuntimeProducerRetrieveTest {

    @Mock
    private BedrockAgentRuntimeClient syncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<RetrieveRequest> capturedRequest;

    @BeforeEach
    public void setup() throws Exception {
        capturedRequest = new AtomicReference<>();
        // Capture the request, return a small synthetic response so the producer's response-handling path is also
        // exercised (results in body + RETRIEVED_RESULTS header, optional nextToken header). lenient(): one of the
        // tests verifies a producer-side validation that throws before reaching the client.
        lenient().doAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0, RetrieveRequest.class));
            KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
                    .content(RetrievalResultContent.builder().text("matched chunk").build())
                    .score(0.87)
                    .build();
            return RetrieveResponse.builder()
                    .retrievalResults(result)
                    .nextToken("page-2-token")
                    .build();
        }).when(syncClient).retrieve(any(RetrieveRequest.class));

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("syncClient", syncClient);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:retrieve")
                        .to("aws-bedrock-agent-runtime://label"
                            + "?bedrockAgentRuntimeClient=#syncClient"
                            + "&operation=retrieve"
                            + "&knowledgeBaseId=kb-1"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");

                from("direct:retrieve-no-kb")
                        .to("aws-bedrock-agent-runtime://label2"
                            + "?bedrockAgentRuntimeClient=#syncClient"
                            + "&operation=retrieve"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");

                from("direct:retrieve-pojo")
                        .to("aws-bedrock-agent-runtime://label3"
                            + "?bedrockAgentRuntimeClient=#syncClient"
                            + "&operation=retrieve"
                            + "&knowledgeBaseId=kb-1"
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
    public void retrieveBuildsRequestFromStringBodyAndEndpointConfig() {
        Exchange result = template.send("direct:retrieve",
                exchange -> exchange.getMessage().setBody("what is camel?"));

        assertNull(result.getException(), "Exchange should not fail");
        RetrieveRequest request = capturedRequest.get();
        assertNotNull(request, "Producer must have invoked the sync client");
        assertEquals("kb-1", request.knowledgeBaseId());
        assertNotNull(request.retrievalQuery(), "Retrieval query must be populated");
        assertEquals("what is camel?", request.retrievalQuery().text());
        // Default knobs (no headers): vector search config is built but no numberOfResults / overrideSearchType set.
        assertNotNull(request.retrievalConfiguration(), "Retrieval configuration must be populated");
        assertNotNull(request.retrievalConfiguration().vectorSearchConfiguration(),
                "Vector search configuration must be populated");
        assertNull(request.retrievalConfiguration().vectorSearchConfiguration().numberOfResults(),
                "numberOfResults must not be set when no header is present");
        assertNull(request.retrievalConfiguration().vectorSearchConfiguration().overrideSearchType(),
                "overrideSearchType must not be set when no header is present");
        assertNull(request.nextToken(), "nextToken must not be set when no header is present");

        // Response should surface results both in body and in a dedicated header.
        Object body = result.getMessage().getBody();
        assertInstanceOf(List.class, body);
        List<?> bodyList = (List<?>) body;
        assertEquals(1, bodyList.size());
        assertInstanceOf(KnowledgeBaseRetrievalResult.class, bodyList.get(0));
        assertEquals("matched chunk", ((KnowledgeBaseRetrievalResult) bodyList.get(0)).content().text());

        Object header = result.getMessage().getHeader(BedrockAgentRuntimeConstants.RETRIEVED_RESULTS);
        assertInstanceOf(List.class, header);
        assertEquals(1, ((List<?>) header).size());
        assertSame(body, header, "Header and body should reference the same result list");

        assertEquals("page-2-token", result.getMessage().getHeader(BedrockAgentRuntimeConstants.NEXT_TOKEN));
    }

    @Test
    public void retrieveAppliesRetrievalConfigHeaders() {
        template.send("direct:retrieve", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.NUMBER_OF_RESULTS, 7);
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.OVERRIDE_SEARCH_TYPE, "HYBRID");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.NEXT_TOKEN, "page-1-token");
            exchange.getMessage().setBody("hello");
        });

        RetrieveRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals(Integer.valueOf(7),
                request.retrievalConfiguration().vectorSearchConfiguration().numberOfResults(),
                "Header CamelAwsBedrockAgentRuntimeNumberOfResults must populate numberOfResults");
        assertEquals(SearchType.HYBRID,
                request.retrievalConfiguration().vectorSearchConfiguration().overrideSearchType(),
                "Header CamelAwsBedrockAgentRuntimeSearchType must populate overrideSearchType");
        assertEquals("page-1-token", request.nextToken(),
                "Header CamelAwsBedrockAgentRuntimeNextToken must populate nextToken");
    }

    @Test
    public void retrieveFailsWhenKnowledgeBaseIdIsMissing() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:retrieve-no-kb", "hi"));
        Throwable cause = ex.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("retrieve operation requires knowledgeBaseId in the endpoint configuration", cause.getMessage());
    }

    @Test
    public void retrieveUsesPojoRequestBodyVerbatim() {
        RetrieveRequest pojo = RetrieveRequest.builder()
                .knowledgeBaseId("kb-from-pojo")
                .retrievalQuery(q -> q.text("pojo question"))
                .build();

        Exchange result = template.send("direct:retrieve-pojo", exchange -> exchange.getMessage().setBody(pojo));

        assertNull(result.getException());
        RetrieveRequest sent = capturedRequest.get();
        assertNotNull(sent);
        // Verify the producer forwarded the POJO without rebuilding it from endpoint config.
        assertSame(pojo, sent,
                "When pojoRequest=true, the RetrieveRequest provided in the body must be sent to the client unchanged");
    }
}
