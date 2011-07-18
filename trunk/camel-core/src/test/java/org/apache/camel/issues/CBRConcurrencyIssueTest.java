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

/**
 * @version 
 */
public class CBRConcurrencyIssueTest extends ContextTestSupport {

    public void testCBRConcurrencyIssue() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:other").expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("seda:start", "Hello World", "foo", "send");
        template.sendBodyAndHeader("seda:start", "Bye World", "foo", "receive");

        assertMockEndpointsSatisfied();
    }

    public void testCBRConcurrencyManyMessagesIssue() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(50);
        getMockEndpoint("mock:other").expectedMessageCount(150);
        
        for (int i = 0; i < 200; i++) {
            if (i % 4 == 0) {
                template.sendBodyAndHeader("seda:start", "Hello World", "foo", "send");
            } else {
                template.sendBodyAndHeader("seda:start", "Bye World", "foo", "receive");
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:start?concurrentConsumers=10")
                    .log("Got foo ${header.foo} header")
                    .choice()
                        .when(header("foo").isEqualTo("send")).to("mock:result")
                        .when(header("foo").isEqualTo("receive")).to("mock:other");
            }
        };
    }

}
