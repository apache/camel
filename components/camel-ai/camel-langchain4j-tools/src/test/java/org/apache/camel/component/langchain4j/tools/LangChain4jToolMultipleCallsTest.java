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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class LangChain4jToolMultipleCallsTest extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What is the weather in london ??\n")
            .invokeTool("FindsTheLatitudeAndLongitudeOfAGivenCity")
            .withParam("name", "London")
            .andThenInvokeTool("ForecastsTheWeatherForTheGivenLatitudeAndLongitude")
            .withParam("latitude", "51.50758961965397")
            .withParam("longitude", "-0.13388057363742217")
            .build();

    private volatile boolean intermediateCalled = false;
    private volatile boolean intermediateHasValidBody = false;

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
                        .to("langchain4j-tools:test1?tags=geo")
                        .log("response is: ${body}");

                from("langchain4j-tools:test1?tags=geo&description=Forecasts the weather for the given latitude and longitude&parameter.latitude=integer&parameters.longitude=integer")
                        .log("intermediate body is: ${body}")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            intermediateCalled = true;
                            if (exchange.getIn().getHeader("longitude", String.class).contains("0") &&
                                    exchange.getIn().getHeader("latitude", String.class).contains("51") &&
                                    body.contains("51.50758961965397") && body.contains("-0.13388057363742217")) {
                                intermediateHasValidBody = true;
                            }
                        })
                        .setBody(simple("""
                                {
                                  "location": "London, UK",
                                  "date": "2025-03-13",
                                  "time": "07:44",
                                  "current_conditions": {
                                    "temperature_celsius": 3,
                                    "temperature_fahrenheit": 37,
                                    "humidity": 93,
                                    "condition": {
                                      "text": "Light rain",
                                      "icon": "drizzle",
                                      "code": 1183
                                    },
                                """));

                from("langchain4j-tools:test1?tags=geo&description=Finds the latitude and longitude of a given city&parameter.name=string")
                        .setBody(simple("{\"latitude\": \"51.50758961965397\", \"longitude\": \"-0.13388057363742217\"}"));

            }
        };
    }

    @Test
    public void testSimpleInvocation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                """
                        You are a meteorologist, and you need to answer questions asked by the user about weather using at most 3 lines.
                         The weather information is a JSON object and has the following fields:
                             maxTemperature is the maximum temperature of the day in Celsius degrees
                             minTemperature is the minimum temperature of the day in Celsius degrees   \s
                             precipitation is the amount of water in mm   \s
                             windSpeed is the speed of wind in kilometers per hour   \s
                             weather is the overall weather.
                        """));
        messages.add(new UserMessage("""
                What is the weather in london ??
                """));

        Exchange message = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(message).isNotNull();
        final String responseContent = message.getMessage().getBody().toString();
        // depending on the reasoning model used to test, the result is different, but we asked for Celcius degree and 3 should be part of it
        Assertions.assertThat(responseContent).containsIgnoringCase("3");
        Assertions.assertThat(intermediateCalled).isTrue();
        Assertions.assertThat(intermediateHasValidBody).isTrue();
    }
}
