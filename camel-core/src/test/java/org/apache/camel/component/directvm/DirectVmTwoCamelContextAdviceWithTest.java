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
package org.apache.camel.component.directvm;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class DirectVmTwoCamelContextAdviceWithTest extends AbstractDirectVmTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:step-1a").routeId("step-1a")
                    .log("Before Step-1a ${body}")
                    .to("direct-vm:step-2a")
                    .log("After Step-1a ${body}");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:step-2a").routeId("step-2a")
                    .log("Before Step-2a ${body}")
                    .setBody(constant("Bye"))
                    .log("After Step-2a ${body}");
            }
        };
    }

    public void testTwoCamelContext() throws Exception {
        // add route
        context.addRoutes(createRouteBuilder());

        // advice
        context.getRouteDefinition("step-1a").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:results");
            }
        });

        // start camel
        context.start();
        context2.start();

        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedBodiesReceived("Bye");

        template.sendBody("direct:step-1a", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
