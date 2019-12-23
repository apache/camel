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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class NoErrorHandlerTest extends ContextTestSupport {

    private static int counter;
    private static boolean jmx = true;

    @Override
    @Before
    public void setUp() throws Exception {
        counter = 0;
        super.setUp();
    }

    @Override
    protected boolean useJmx() {
        return jmx;
    }

    @Test
    public void testNoErrorHandler() throws Exception {
        doTest();
    }

    @Test
    public void testNoErrorHandlerJMXDisabled() throws Exception {
        doTest();
    }

    private void doTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Goodday World");

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
        try {
            template.sendBody("direct:start", "Bye World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
        template.sendBody("direct:start", "Goodday World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:start").process(exchange -> {
                    if (++counter < 3) {
                        throw new IllegalArgumentException("Forced by unit test");
                    }
                }).to("mock:result");
            }
        };
    }
}
