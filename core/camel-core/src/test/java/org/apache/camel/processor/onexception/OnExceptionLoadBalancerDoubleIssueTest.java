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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnExceptionLoadBalancerDoubleIssueTest extends ContextTestSupport {

    @Test
    public void testNotDouble() throws Exception {
        // there should only be 3 processors on the load balancer
        getMockEndpoint("mock:error").expectedBodiesReceived("A", "D", "G");
        getMockEndpoint("mock:error2").expectedBodiesReceived("B", "E");
        getMockEndpoint("mock:error3").expectedBodiesReceived("C", "F");

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:bar", "C");
        template.sendBody("direct:bar", "D");
        template.sendBody("direct:foo", "E");
        template.sendBody("direct:bar", "F");
        template.sendBody("direct:foo", "G");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("direct:error");
                from("direct:error").loadBalance().roundRobin().id("round").to("mock:error", "mock:error2", "mock:error3");

                from("direct:foo").throwException(new IllegalArgumentException("Forced"));

                from("direct:bar").throwException(new IllegalArgumentException("Also Forced"));
            }
        };
    }
}
