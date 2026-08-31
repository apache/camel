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
package org.apache.camel.component.langchain4j.chat;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiErrorCategory;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jChatErrorMetadataTest extends CamelTestSupport {

    private final AtomicReference<Exchange> failedExchange = new AtomicReference<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("chatModel", new FailingChatModel());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Properties properties = new Properties();
                properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
                context.getPropertiesComponent().setOverrideProperties(properties);

                onException(RateLimitException.class)
                        .process(exchange -> failedExchange.set(exchange))
                        .handled(true);

                from("direct:start")
                        .to("langchain4j-chat:test?chatModel=#chatModel")
                        .to("mock:result");
            }
        };
    }

    @Test
    void shouldExposeErrorCategoryWhenObservabilityDisabled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = failedExchange.get();
        assertThat(exchange).isNotNull();
        assertThat(exchange.getProperty(GenAiErrorProperties.ERROR_CATEGORY, String.class))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT.name());
        assertThat(exchange.getProperty(GenAiErrorProperties.RETRY_AFTER_MILLIS)).isNull();
    }

    private static final class FailingChatModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new RateLimitException("quota exceeded");
        }

        @Override
        public ChatResponse chat(ChatMessage... messages) {
            throw new RateLimitException("quota exceeded");
        }
    }
}
