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

package org.apache.camel.component.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OpenAIProducerMockTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock()
            .builder()
            .when("hello")
            .replyWith("Hi from mock")
            .end()
            .when("json please")
            .replyWith("{\"ok\":true}")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Basic chat route using the mock server
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                                + "/v1");

                // Streaming chat route using the mock server
                from("direct:chat-stream")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&streaming=true&baseUrl="
                                + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void basicChatReturnsMockedResponse() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("hello"));
        assertEquals("Hi from mock", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
        assertEquals("openai-mock", result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL));
    }

    @Test
    void jsonSchemaHeaderParsesJsonContent() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody("json please");
            e.getIn()
                    .setHeader(
                            OpenAIConstants.JSON_SCHEMA,
                            "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
        });
        String body = result.getMessage().getBody(String.class);
        assertNotNull(body);
        assertEquals("{\"ok\":true}", body);
    }

    @Test
    void streamingChatReturnsIteratorOfChunks() {
        Exchange result = template.request("direct:chat-stream", e -> e.getIn().setBody("hello"));

        Object body = result.getMessage().getBody();
        assertNotNull(body);
        assertTrue(body instanceof java.util.Iterator);
    }
}
