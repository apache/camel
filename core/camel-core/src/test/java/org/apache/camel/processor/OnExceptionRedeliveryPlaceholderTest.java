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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class OnExceptionRedeliveryPlaceholderTest extends ContextTestSupport {

    private static String counter = "";

    @Test
    public void testRedeliveryPlaceholder() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("123", counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getPropertiesComponent().addInitialProperty("myCount", "3");
                context.getPropertiesComponent().addInitialProperty("myFac", "1");

                from("direct:start")
                        .onException(Exception.class)
                        .maximumRedeliveryDelay(1)
                        .maximumRedeliveries("{{myCount}}")
                        .collisionAvoidanceFactor("{{myFac}}")
                        .onRedelivery(e -> {
                            counter += e.getMessage().getHeader(Exchange.REDELIVERY_COUNTER);
                        })
                        .handled(true)
                        .to("mock:error")
                        .end()
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
