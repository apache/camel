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
package org.apache.camel.component.langchain4j.tools;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that using the stop() EIP in a tool route does not break parallel tool invocations.
 *
 * When the LLM requests multiple tools in a single response, each tool route must execute independently. Previously,
 * using stop() in one tool route would cause the ROUTE_STOP flag to leak into subsequent tool invocations, preventing
 * them from executing and returning the wrong result.
 */
public class LangChain4jToolStopEipTest extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("please amend 123, and get me the promotions\n")
            .invokeTool("AmendAnOrderByItsID")
            .withParam("orderId", "123")
            .andInvokeTool("GetCurrentPromotions")
            .build();

    private volatile boolean amendOrderCalled = false;
    private volatile boolean getPromotionsCalled = false;
    private volatile String getPromotionsBody = null;
    private volatile boolean routeContinuedAfterTools = false;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ToolsHelper.createModel(openAIMock.getBaseUrl());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);
        component.getConfiguration().setChatModel(chatModel);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:test")
                        .to("langchain4j-tools:test1?tags=orders")
                        .process(exchange -> routeContinuedAfterTools = true);

                // Tool1: uses stop() mid-way — must not leak ROUTE_STOP to Tool2
                from("langchain4j-tools:test1?tags=orders&description=Amend an order by its ID&parameter.orderId=string")
                        .process(exchange -> {
                            amendOrderCalled = true;
                        })
                        .setBody(constant("order amended"))
                        .stop();

                // Tool2: returns JSON content — must execute independently despite Tool1's stop()
                from("langchain4j-tools:test1?tags=orders&description=Get current promotions")
                        .setBody(constant("{\"status\":\"ok\",\"promotions\":[\"10% off\"]}"))
                        .process(exchange -> {
                            getPromotionsCalled = true;
                            getPromotionsBody = exchange.getIn().getBody(String.class);
                        });
            }
        };
    }

    @Test
    public void testParallelToolsWithStopEip() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are an assistant that helps with orders and promotions."));
        messages.add(new UserMessage("please amend 123, and get me the promotions\n"));

        Exchange result = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        assertThat(result).isNotNull();

        // Both tool routes must have been called
        assertThat(amendOrderCalled).as("AmendOrder tool should have been called").isTrue();
        assertThat(getPromotionsCalled).as("GetPromotions tool should have been called").isTrue();

        // GetPromotions must return its own body, not AmendOrder's body
        assertThat(getPromotionsBody).as("GetPromotions should return its own body, not AmendOrder's")
                .contains("promotions")
                .doesNotContain("order amended");

        // The final response should contain the promotions data
        String responseContent = result.getMessage().getBody(String.class);
        assertThat(responseContent).isNotNull();
        assertThat(responseContent).contains("promotions");

        // The calling route must continue after the tools producer —
        // stop() in a tool route must not leak into the response exchange
        assertThat(routeContinuedAfterTools).as("Route should continue after tools producer").isTrue();
    }
}
