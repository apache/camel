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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BeanThrowAssertionErrorTest extends ContextTestSupport {

    @Test
    public void testAssertion() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        template.sendBody("direct:start", "Hello Camel");
        assertMockEndpointsSatisfied();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should fail");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void testAssertionProcessor() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start2", "Hello World");
        assertMockEndpointsSatisfied();

        try {
            template.sendBody("direct:start2", "Hello Camel");
            fail("Should fail");
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .bean(BeanThrowAssertionErrorTest.this, "doSomething")
                        .to("mock:result");

                from("direct:start2")
                        .bean(new MyProcessorBean())
                        .to("mock:result");
            }
        };
    }

    public void doSomething(String body) {
        assertEquals("Hello Camel", body);
    }

    private static class MyProcessorBean implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            assertEquals("Hello World", exchange.getMessage().getBody());
        }
    }

}
