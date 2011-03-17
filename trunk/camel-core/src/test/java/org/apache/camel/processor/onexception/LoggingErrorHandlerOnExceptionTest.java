/**
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
package org.apache.camel.processor.onexception;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class LoggingErrorHandlerOnExceptionTest extends ContextTestSupport {

    public void testLoggingErrorHandler() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        try {
            template.sendBody("direct:kaboom", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kaboom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testLoggingErrorHandlerOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        template.sendBody("direct:damn", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                errorHandler(loggingErrorHandler("org.apache.camel.MyLogger"));

                onException(IllegalArgumentException.class)
                    .handled(true)
                    .to("mock:handled");

                from("direct:kaboom").to("log:kaboom").throwException(new Exception("Kaboom"));
                from("direct:damn").to("log:damn").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
