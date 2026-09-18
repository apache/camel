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
package org.apache.camel.component.springai.chat;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiErrorCategory;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatErrorMetadataTest extends CamelTestSupport {

    private final AtomicReference<Exchange> failedExchange = new AtomicReference<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Properties properties = new Properties();
                properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
                context.getPropertiesComponent().setOverrideProperties(properties);

                onException(RuntimeException.class)
                        .process(exchange -> failedExchange.set(exchange))
                        .handled(true);

                from("direct:start")
                        .to("spring-ai-chat:test")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SpringAiChatComponent component = new SpringAiChatComponent();
        component.setChatModel(new FailingChatModel());
        context.addComponent("spring-ai-chat", component);
        return context;
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
                .isEqualTo(GenAiErrorCategory.SERVER_ERROR.name());
    }

    private static final class FailingChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new org.springframework.ai.retry.TransientAiException("temporary upstream failure");
        }
    }
}
