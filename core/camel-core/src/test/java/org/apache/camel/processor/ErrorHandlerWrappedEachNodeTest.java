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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * Unit test for verifying that error handler is wrapped each individual node in a pipeline. Based on CAMEL-1548.
 */
public class ErrorHandlerWrappedEachNodeTest extends ContextTestSupport {

    private static int kaboom;
    private static int hi;

    @Test
    public void testKaboom() throws Exception {
        kaboom = 0;
        hi = 0;

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hi Kaboom");

        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();

        // we invoke kaboom 3 times
        assertEquals(3, kaboom);
        // but hi is only invoke 1 time
        assertEquals(1, hi);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // use dead letter channel that supports redeliveries
                errorHandler(deadLetterChannel("mock:error")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0)
                        .logStackTrace(false));

                from("direct:start")
                        .pipeline("bean:foo?method=hi", "bean:foo?method=kaboom")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    public static final class MyFooBean {

        public void kaboom() {
            if (kaboom++ < 2) {
                throw new IllegalArgumentException("Kaboom");
            }
        }

        public String hi(String payload) {
            hi++;
            return "Hi " + payload;
        }
    }
}
