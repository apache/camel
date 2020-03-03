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

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 *
 */
public class ContextScopedOnExceptionNotHandledRouteScopedErrorHandlerRefIssueTwoRoutesTest extends ContextTestSupport {

    @Test
    public void testOnExceptionErrorHandlerRef() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionErrorHandlerRefFoo() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myDLC", new DeadLetterChannelBuilder("mock:dead"));
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(false).to("mock:handled").end();

                from("direct:foo").errorHandler(new ErrorHandlerBuilderRef("myDLC")).to("mock:foo").throwException(new IOException("Damn IO"));

                from("direct:start").errorHandler(new ErrorHandlerBuilderRef("myDLC")).to("mock:a").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
