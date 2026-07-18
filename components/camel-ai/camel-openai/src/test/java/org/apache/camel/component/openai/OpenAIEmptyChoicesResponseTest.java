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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reproducer for a robustness gap: a chat completion response with an empty {@code choices} array fails the exchange
 * with a raw {@code IndexOutOfBoundsException} because {@code OpenAIProducer.processNonStreamingSimple} dereferences
 * {@code response.choices().get(0)} unguarded — while {@code setResponseHeaders} in the same class does guard against
 * empty choices. Empty {@code choices} payloads are known in the OpenAI-compatible ecosystem from the streaming side
 * (Azure OpenAI content-filter chunks, OpenRouter usage-only final chunks); for non-streaming this is defensive
 * hardening against misbehaving compatible shims/proxies, which should surface as a meaningful exception.
 */
public class OpenAIEmptyChoicesResponseTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .thenRespondWith((exchange, input) -> {
                try {
                    Map<String, Object> completion = new HashMap<>();
                    completion.put("id", UUID.randomUUID().toString());
                    completion.put("choices", List.of());
                    completion.put("created", System.currentTimeMillis() / 1000L);
                    completion.put("model", "openai-mock");
                    completion.put("object", "chat.completion");
                    return MAPPER.writeValueAsString(completion);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void emptyChoicesMustFailWithMeaningfulException() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("hello"));

        assertNotNull(result.getException(), "An empty choices array cannot produce a response body");
        assertFalse(result.getException() instanceof IndexOutOfBoundsException,
                "An empty choices array must surface as a meaningful exception describing the malformed "
                                                                                + "provider response, not a raw IndexOutOfBoundsException. Got: "
                                                                                + result.getException());
    }
}
