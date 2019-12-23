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
import java.sql.SQLException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 *
 */
public class AdviceWithOnExceptionAndInterceptTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    class MyAdviceWithRouteBuilder extends AdviceWithRouteBuilder {
        @Override
        public void configure() {
            onException(SQLException.class).handled(true).transform(constant("Intercepted SQL!")).log("sending ${body}").to("mock:b");

            interceptSendToEndpoint("mock:a").skipSendToOriginalEndpoint().log("intercepted message").bean(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    throw new SQLException();
                }
            });
        }
    }

    @Test
    public void testFailover() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").loadBalance().failover(IOException.class).to("mock:a").to("mock:b").end();
            }
        });

        RouteDefinition routeDefinition = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(routeDefinition, context, new MyAdviceWithRouteBuilder());
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Intercepted SQL!");

        template.sendBody("direct:a", "foo");

        assertMockEndpointsSatisfied();
    }

}
