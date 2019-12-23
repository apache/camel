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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test inspired by end user
 */
public class OnExceptionHandleAndTransformTest extends ContextTestSupport {

    @Test
    public void testOnExceptionTransformConstant() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(0));

                // START SNIPPET: e1
                // we catch MyFunctionalException and want to mark it as handled
                // (= no failure returned to client)
                // but we want to return a fixed text response, so we transform
                // OUT body as Sorry.
                onException(MyFunctionalException.class).handled(true).transform().constant("Sorry");
                // END SNIPPET: e1

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Sorry you cannot do this");
                    }
                });
            }
        });

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Sorry", out);
    }

    @Test
    public void testOnExceptionTransformExceptionMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(0));

                // START SNIPPET: e2
                // we catch MyFunctionalException and want to mark it as handled
                // (= no failure returned to client)
                // but we want to return a fixed text response, so we transform
                // OUT body and return the exception message
                onException(MyFunctionalException.class).handled(true).transform(exceptionMessage());
                // END SNIPPET: e2

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Sorry you cannot do this again to me");
                    }
                });
            }
        });

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Sorry you cannot do this again to me", out);
    }

    @Test
    public void testOnExceptionSimpleLangaugeExceptionMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(0));

                // START SNIPPET: e3
                // we catch MyFunctionalException and want to mark it as handled
                // (= no failure returned to client)
                // but we want to return a fixed text response, so we transform
                // OUT body and return a nice message
                // using the simple language where we want insert the exception
                // message
                onException(MyFunctionalException.class).handled(true).transform().simple("Error reported: ${exception.message} - cannot process this message.");
                // END SNIPPET: e3

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Out of order");
                    }
                });
            }
        });

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Error reported: Out of order - cannot process this message.", out);
    }

}
