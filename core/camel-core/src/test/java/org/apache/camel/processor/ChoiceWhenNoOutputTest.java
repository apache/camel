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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ChoiceWhenNoOutputTest extends ContextTestSupport {

    @Test
    public void testWhenNoOutput() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:2").expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("direct:start", "Hello World", "test", "1");
        template.sendBodyAndHeader("direct:start", "Bye World", "test", "2");
        try {
            template.sendBodyAndHeader("direct:start", "Hi World", "test", "3");
            fail();
        } catch (Exception e) {
            Assertions.assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Validation error!", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .choice()
                        .when(header("test").isEqualTo("1"))
                        .when(header("test").isEqualTo("2"))
                            .to("mock:2")
                        .otherwise()
                            .throwException(new IllegalArgumentException("Validation error!"))
                        .end()
                        .to("mock:result");
            }
        };
    }

}
