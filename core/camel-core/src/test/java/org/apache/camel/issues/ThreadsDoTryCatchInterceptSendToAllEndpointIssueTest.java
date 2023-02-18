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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class ThreadsDoTryCatchInterceptSendToAllEndpointIssueTest extends ContextTestSupport {

    @Test
    public void testThreadsTryCatch() throws Exception {
        getMockEndpoint("mock:log:try").expectedMessageCount(1);
        getMockEndpoint("mock:log:catch").expectedMessageCount(1);
        getMockEndpoint("mock:log:world").expectedMessageCount(1);
        getMockEndpoint("mock:log:other").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // mock all endpoints
        context.getCamelContextExtension().registerEndpointCallback(new InterceptSendToMockEndpointStrategy("*"));

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").threads().doTry().to("log:try").throwException(new IllegalArgumentException("Forced"))
                        .doCatch(Exception.class).to("log:catch").choice()
                        .when(body().contains("World")).to("log:world").stop().otherwise().to("log:other").stop().end().end();
            }
        };
    }
}
