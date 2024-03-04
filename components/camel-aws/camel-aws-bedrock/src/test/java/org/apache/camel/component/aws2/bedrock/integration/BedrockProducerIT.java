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
package org.apache.camel.component.aws2.bedrock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.BedrockConstants;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedrockProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testInvokeTitanExpressModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_express", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode(
                            "User: Generate synthetic data for daily product sales in various categories - include row number, product name, category, date of sale and price. Produce output in JSON format. Count records and ensure there are no more than 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(1024));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanLiteModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_lite", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode(
                            "User: Generate synthetic data for daily product sales in various categories - include row number, product name, category, date of sale and price. Produce output in JSON format. Count records and ensure there are no more than 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(1024));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanImageModel() throws InterruptedException {

        result.expectedMessageCount(3);
        final Exchange result = template.send("direct:send_titan_image", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            ObjectNode textParameter = mapper.createObjectNode();
            textParameter.putIfAbsent("text",
                    new TextNode("A Sci-fi camel running in the desert"));
            rootNode.putIfAbsent("textToImageParams", textParameter);
            rootNode.putIfAbsent("taskType", new TextNode("TEXT_IMAGE"));
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("numberOfImages", new IntNode(3));
            childNode.putIfAbsent("quality", new TextNode("standard"));
            childNode.putIfAbsent("cfgScale", new IntNode(8));
            childNode.putIfAbsent("height", new IntNode(512));
            childNode.putIfAbsent("width", new IntNode(512));
            childNode.putIfAbsent("seed", new IntNode(0));

            rootNode.putIfAbsent("imageGenerationConfig", childNode);

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanEmbeddingsModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_embeddings", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode("A Sci-fi camel running in the desert"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "*/*");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send_titan_express")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=eu-central-1&operation=invokeTextModel&modelId="
                            + BedrockModels.TITAN_TEXT_EXPRESS_V1.model)
                        .to(result);

                from("direct:send_titan_lite")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.TITAN_TEXT_LITE_V1.model)
                        .to(result);

                from("direct:send_titan_image")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=us-east-1&operation=invokeImageModel&modelId="
                            + BedrockModels.TITAN_IMAGE_GENERATOR_V1.model)
                        .split(body())
                        .unmarshal().base64()
                        .setHeader("CamelFileName", simple("image-${random(128)}.png")).to("file:target/generated_images")
                        .to(result);

                from("direct:send_titan_embeddings")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=us-east-1&operation=invokeEmbeddingsModel&modelId="
                            + BedrockModels.TITAN_EMBEDDINGS_G1.model)
                        .to(result);
            }
        };
    }
}
