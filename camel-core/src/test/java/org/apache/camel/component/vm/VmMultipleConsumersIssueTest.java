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
package org.apache.camel.component.vm;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class VmMultipleConsumersIssueTest extends ContextTestSupport {

    @Test
    public void testVmMultipleConsumersIssue() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World");

        template.sendBody("direct:inbox", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:inbox")
                    .to(ExchangePattern.InOut, "vm:foo?timeout=5000")
                    .to("mock:done");

                from("vm:foo?multipleConsumers=true")
                    .to("log:a")
                    .to("mock:a");

                from("vm:foo?multipleConsumers=true")
                    .to("log:b")
                    .to("mock:b");
            }
        };
    }
}
