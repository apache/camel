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

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ErrorOccurredInOnExceptionRoute extends ContextTestSupport {

    // TODO: fails when run individually but works with mvn clean install and run all tests

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testErrorInOnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(MyTechnicalException.class)
                    .handled(true)
                    .to("mock:tech");

                onException(MyFunctionalException.class)
                    .handled(true)
                    .to("mock:onFunc")
                    .throwException(new MyTechnicalException("Tech error"))
                    .to("mock:doneFunc");

                // in this regular route the processing failed
                from("direct:start")
                    .throwException(new MyFunctionalException("Func error"));
            }
        });
        context.start();

        getMockEndpoint("mock:onFunc").expectedMessageCount(1);
        getMockEndpoint("mock:doneFunc").expectedMessageCount(0);
        getMockEndpoint("mock:tech").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testErrorInOnExceptionNotHandledSecondOnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IOException.class)
                    // we cannot handle this exception so it should propagate back
                    .to("mock:tech");

                onException(MyFunctionalException.class)
                    .handled(true)
                    .to("mock:onFunc")
                    .throwException(new IOException("Cannot do this"))
                    .to("mock:doneFunc");

                // in this regular route the processing failed
                from("direct:start")
                    .throwException(new MyFunctionalException("Func error"));
            }
        });
        context.start();

        getMockEndpoint("mock:onFunc").expectedMessageCount(1);
        getMockEndpoint("mock:doneFunc").expectedMessageCount(0);
        getMockEndpoint("mock:tech").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IOException.class, e.getCause());
            assertEquals("Cannot do this", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testErrorInOnExceptionNotHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(MyFunctionalException.class)
                    .handled(true)
                    .to("mock:onFunc")
                    .throwException(new IOException("Cannot do this"))
                    .to("mock:doneFunc");

                // in this regular route the processing failed
                from("direct:start")
                    .throwException(new MyFunctionalException("Func error"));
            }
        });
        context.start();

        getMockEndpoint("mock:onFunc").expectedMessageCount(1);
        getMockEndpoint("mock:doneFunc").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IOException.class, e.getCause());
            assertEquals("Cannot do this", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

}
