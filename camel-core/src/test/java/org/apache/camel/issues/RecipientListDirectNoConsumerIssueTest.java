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
import org.apache.camel.component.direct.DirectComponent;

/**
 *
 */
public class RecipientListDirectNoConsumerIssueTest extends ContextTestSupport {

    public void testDirectNoConsumerOneMessage() throws Exception {
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:foo;direct:foo");

        assertMockEndpointsSatisfied();
    }

    public void testDirectNoConsumerTwoMessages() throws Exception {
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:foo");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", "direct:foo");

        assertMockEndpointsSatisfied();
    }

    public void testDirectNoConsumerOneMessageBar() throws Exception {
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("direct:bar", "Hello World", "bar", "mock:foo;direct:foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getComponent("direct", DirectComponent.class).setBlock(false);
                
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:start")
                    .recipientList().header("foo").delimiter(";");

                from("direct:bar")
                    .recipientList(";").header("bar");
            }
        };
    }
}
