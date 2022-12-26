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
package org.apache.camel.model;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.processor.OnCompletionTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class RouteConfigurationOnCompletionTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onCompletion().onCompleteOnly().to("log:ok").to("mock:ok");
                routeConfiguration().onCompletion().onFailureOnly().to("log:fail").to("mock:fail");
            }
        });

        return camelContext;
    }

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:ok").expectedMessageCount(1);
        getMockEndpoint("mock:fail").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFail() throws Exception {
        getMockEndpoint("mock:ok").expectedMessageCount(0);
        getMockEndpoint("mock:fail").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOkAndFail() throws Exception {
        getMockEndpoint("mock:ok").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:fail").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("direct:end");

                from("direct:end")
                        // CAMEL-18835: apply the processor by this route and not the one above to
                        // enforce multiple calls to the OnCompletionProcessor#onFailure callback
                        .process(new OnCompletionTest.MyProcessor())
                        .to("mock:result");
            }
        };
    }

}
