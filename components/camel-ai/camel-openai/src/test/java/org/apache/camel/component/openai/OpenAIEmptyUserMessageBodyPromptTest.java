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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the userMessage option is configured to an empty string, it must not shadow the prompt supplied in the message
 * body. An operator-precedence bug used to set the (empty) configured message as the prompt, dropping the body and
 * failing with "No input provided".
 */
public class OpenAIEmptyUserMessageBodyPromptTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello from body")
            .replyWith("mock reply")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:empty-user-message")
                        .to("openai:chat-completion?model=gpt-4&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void bodyPromptIsUsedWhenConfiguredUserMessageIsEmpty() {
        // Configure the route's endpoint with an empty userMessage - the exact case the precedence bug mishandled.
        OpenAIEndpoint endpoint = context.getEndpoints().stream()
                .filter(OpenAIEndpoint.class::isInstance)
                .map(OpenAIEndpoint.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no OpenAIEndpoint found"));
        endpoint.getConfiguration().setUserMessage("");

        Exchange result = template.request("direct:empty-user-message", e -> e.getIn().setBody("hello from body"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("mock reply");
    }
}
