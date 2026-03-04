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
package org.apache.camel.component.langchain4j.agent.integration;

import java.io.ByteArrayInputStream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LangChain4jAgentAutoConversionIT extends CamelTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {

        Agent mockAgent = (body, exchange) -> "Processed";

        context.getRegistry().bind("mockAgent", mockAgent);

        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("langchain4j-agent:test?agent=#mockAgent")
                        .to("mock:result");
            }
        };

    }

    @Test
    void shouldAutoConvertPlainString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello world");

        mock.assertIsSatisfied();

        String response = mock.getExchanges().get(0)
                .getMessage()
                .getMandatoryBody(String.class);

        assertNotNull(response);
    }

    @Test
    void shouldAutoConvertInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        try {
            template.sendBody("direct:start",
                    new ByteArrayInputStream("Hello stream".getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        mock.assertIsSatisfied();
    }

}
