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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class TryCatchSetHeaderIssueTest extends ContextTestSupport {

    @Test
    public void testTryCatchSetHeaderIssue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .setHeader("foo", constant("try"))
                        .throwException(new IllegalArgumentException("Damn"))
                    .doCatch(Exception.class)
                        .setHeader("foo", constant("error"))
                    .end()
                    .to("mock:end");
            }
        });
        context.start();

        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:end").expectedHeaderReceived("foo", "error");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTryCatchTwoSetHeaderIssue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .setHeader("foo", constant("try"))
                        .throwException(new IllegalArgumentException("Damn"))
                    .doCatch(IllegalArgumentException.class)
                        .setHeader("foo", constant("error"))
                    .doCatch(Exception.class)
                        .setHeader("foo", constant("damn"))
                    .end()
                    .to("mock:end");
            }
        });
        context.start();

        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:end").expectedHeaderReceived("foo", "error");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        };
    }
}
