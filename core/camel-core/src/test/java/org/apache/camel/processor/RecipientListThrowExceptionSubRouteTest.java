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
import org.junit.Test;

/**
 *
 */
public class RecipientListThrowExceptionSubRouteTest extends ContextTestSupport {

    public static class Router {
        public String findEndpoint() {
            throw new IllegalArgumentException("Damn");
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRecipientList() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").onException(Exception.class).to("mock:error").end().recipientList(method(Router.class));
            }
        });
        context.start();

        MockEndpoint endpoint = getMockEndpoint("mock:error");
        endpoint.expectedMessageCount(1);

        sendBody("direct:start", "Hello World!");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRecipientListChild() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").onException(Exception.class).to("mock:error").end().to("direct:child");

                from("direct:child").errorHandler(noErrorHandler()).recipientList(method(Router.class));
            }
        });
        context.start();

        MockEndpoint endpoint = getMockEndpoint("mock:error");
        endpoint.expectedMessageCount(1);

        sendBody("direct:start", "Hello World!");

        assertMockEndpointsSatisfied();
    }

}
