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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ChoiceEndOrEndChoiceIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testEndChoiceInvalid() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .choice()
                            .when(header("number").isEqualTo("one")).to("mock:one")
                            .when(header("number").isEqualTo("two")).to("mock:two")
                            .when(header("number").isEqualTo("three")).to("mock:three").endChoice()
                            .to("mock:finally");
                }
            });
        }, "Should have thrown exception");

        assertEquals("A new choice clause should start with a when() or otherwise()."
                     + " If you intend to end the entire choice and are using endChoice() then use end() instead.",
                e.getMessage());
    }

    @Test
    public void testEndChoiceValid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                            .when(header("number").isEqualTo("one")).to("mock:one")
                            .when(header("number").isEqualTo("two")).to("mock:two")
                            .when(header("number").isEqualTo("three")).to("mock:three")
                        .end()
                        .to("mock:finally");
            }
        });
        context.start();

        getMockEndpoint("mock:one").expectedHeaderReceived("number", "one");
        getMockEndpoint("mock:two").expectedHeaderReceived("number", "two");
        getMockEndpoint("mock:three").expectedHeaderReceived("number", "three");
        getMockEndpoint("mock:finally").expectedBodiesReceived("1", "2", "3");

        template.sendBodyAndHeader("direct:start", "1", "number", "one");
        template.sendBodyAndHeader("direct:start", "2", "number", "two");
        template.sendBodyAndHeader("direct:start", "3", "number", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndChoiceEndValid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                            .when(header("number").isEqualTo("one")).to("mock:one")
                            .when(header("number").isEqualTo("two")).to("mock:two")
                            .when(header("number").isEqualTo("three")).to("mock:three").endChoice()
                        .end()
                        .to("mock:finally");
            }
        });
        context.start();

        getMockEndpoint("mock:one").expectedHeaderReceived("number", "one");
        getMockEndpoint("mock:two").expectedHeaderReceived("number", "two");
        getMockEndpoint("mock:three").expectedHeaderReceived("number", "three");
        getMockEndpoint("mock:finally").expectedBodiesReceived("1", "2", "3");

        template.sendBodyAndHeader("direct:start", "1", "number", "one");
        template.sendBodyAndHeader("direct:start", "2", "number", "two");
        template.sendBodyAndHeader("direct:start", "3", "number", "three");

        assertMockEndpointsSatisfied();
    }

}
