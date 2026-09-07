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

class OpenAIRouteToolReturnDirectTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("call route tool")
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:get_weather"
                     + "?tags=openai-test"
                     + "&description=Get weather for a city"
                     + "&parameter.city=string"
                     + "&parameter.city.required=true"
                     + "&returnDirect=true")
                        .setBody(simple("Direct route result: Sunny in ${header.city}"));

                from("direct:route-tool-chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                            + "&tags=openai-test&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void routeToolReturnDirectShortCircuitsAgenticLoop() {
        Exchange result = template.request("direct:route-tool-chat", e -> e.getIn().setBody("call route tool"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Direct route result: Sunny in London");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class)).isTrue();
        assertThat(result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class)).isEqualTo(1);
    }
}
