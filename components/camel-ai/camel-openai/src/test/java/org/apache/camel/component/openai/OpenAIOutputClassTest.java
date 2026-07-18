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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenAIOutputClassTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .replyWith("Hi from mock")
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
    void unknownOutputClassViaHeaderShouldFail() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader(OpenAIConstants.OUTPUT_CLASS, "com.does.not.Exist");
        });

        assertThat(result.getException())
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessageContaining("com.does.not.Exist");
    }

    @Test
    void unknownOutputClassOnEndpointShouldFailOnStartup() throws Exception {
        CamelContext ctx = createCamelContext();
        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bad")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&outputClass=com.does.not.Exist&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        });

        Exception thrown = assertThrows(FailedToStartRouteException.class, ctx::start);

        assertThat(thrown)
                .rootCause()
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessageContaining("com.does.not.Exist");

        ctx.stop();
    }
}
