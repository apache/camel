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
package org.apache.camel.component.aws2.bedrock.runtime.integration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.component.aws2.bedrock.runtime.BedrockConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BedrockProducerStreamingIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockProducerStreamingIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeEach
    public void resetMocks() {
        result.reset();
    }

    @Test
    @Order(1)
    public void testInvokeTitanExpressModelStreamingComplete() throws InterruptedException {
        LOG.info("Starting testInvokeTitanExpressModelStreamingComplete");

        result.expectedMessageCount(1);
        result.setResultWaitTime(TimeUnit.SECONDS.toMillis(30));
        final Exchange resultExchange = template.send("direct:send_titan_express_streaming_complete", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText", new TextNode("Write a short poem about Apache Camel."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(512));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);

        // Verify response
        String response = resultExchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");
        LOG.info("Received response with length: {}", response.length());

        // Verify streaming metadata headers
        Integer chunkCount = resultExchange.getMessage().getHeader(BedrockConstants.STREAMING_CHUNK_COUNT, Integer.class);
        assertNotNull(chunkCount, "Chunk count header should be present");
        assertTrue(chunkCount > 0, "Chunk count should be greater than 0, but was: " + chunkCount);
        LOG.info("Received {} chunks in complete mode", chunkCount);

        // Optional: Verify completion reason if available
        String completionReason
                = resultExchange.getMessage().getHeader(BedrockConstants.STREAMING_COMPLETION_REASON, String.class);
        if (completionReason != null) {
            LOG.info("Completion reason: {}", completionReason);
        }
    }

    @Test
    @Order(2)
    public void testInvokeTitanExpressModelStreamingChunks() throws InterruptedException {
        LOG.info("Starting testInvokeTitanExpressModelStreamingChunks");

        result.expectedMessageCount(1);
        result.setResultWaitTime(TimeUnit.SECONDS.toMillis(30));
        final Exchange resultExchange = template.send("direct:send_titan_express_streaming_chunks", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText", new TextNode("Count from 1 to 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(100));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);

        // Verify response - should be a list of chunks
        @SuppressWarnings("unchecked")
        List<String> chunks = resultExchange.getMessage().getBody(List.class);
        assertNotNull(chunks, "Chunks list should not be null");
        assertTrue(chunks.size() > 0, "Chunks list should not be empty, but was: " + chunks.size());
        LOG.info("Received {} text chunks in chunks mode", chunks.size());

        // Log first few chunks for debugging
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            LOG.info("Chunk {}: '{}'", i, chunks.get(i));
        }

        // Verify streaming metadata headers
        Integer chunkCount = resultExchange.getMessage().getHeader(BedrockConstants.STREAMING_CHUNK_COUNT, Integer.class);
        assertNotNull(chunkCount, "Chunk count header should be present");
        assertTrue(chunkCount > 0, "Chunk count should be greater than 0, but was: " + chunkCount);
        LOG.info("Chunk count from header: {}", chunkCount);

        // Verify chunks list size matches or is close to header count
        // Note: chunks list contains only non-empty text chunks, while header counts all chunks
        assertTrue(chunks.size() <= chunkCount,
                "Text chunks (" + chunks.size() + ") should be <= total chunks (" + chunkCount + ")");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send_titan_express_streaming_complete")
                        .to("aws-bedrock:test?accessKey=RAW(" + System.getProperty("aws.manual.access.key")
                            + ")&secretKey=RAW(" + System.getProperty("aws.manual.secret.key")
                            + ")&operation=invokeTextModelStreaming&modelId="
                            + BedrockModels.TITAN_TEXT_EXPRESS_V1.model
                            + "&region=us-east-1&streamOutputMode=complete")
                        .to(result);

                from("direct:send_titan_express_streaming_chunks")
                        .to("aws-bedrock:test?accessKey=RAW(" + System.getProperty("aws.manual.access.key")
                            + ")&secretKey=RAW(" + System.getProperty("aws.manual.secret.key")
                            + ")&operation=invokeTextModelStreaming&modelId="
                            + BedrockModels.TITAN_TEXT_EXPRESS_V1.model
                            + "&region=us-east-1&streamOutputMode=chunks")
                        .to(result);
            }
        };
    }
}
