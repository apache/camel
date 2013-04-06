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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class BeanMethodNameHeaderIssueTest extends ContextTestSupport {

    public void testBeanMethodNameHeaderIssue() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("foo");
        getMockEndpoint("mock:a").message(0).header(Exchange.BEAN_METHOD_NAME).isNull();
        getMockEndpoint("mock:b").expectedBodiesReceived("bar");
        getMockEndpoint("mock:b").message(0).header(Exchange.BEAN_METHOD_NAME).isNull();
        getMockEndpoint("mock:c").expectedBodiesReceived("Bye bar");
        getMockEndpoint("mock:c").message(0).header(Exchange.BEAN_METHOD_NAME).isNull();
        getMockEndpoint("mock:d").expectedBodiesReceived("Bye bar Bye bar");
        getMockEndpoint("mock:d").message(0).header(Exchange.BEAN_METHOD_NAME).isNull();

        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.BEAN_METHOD_NAME, "foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(BeanMethodNameHeaderIssueTest.class)
                    .to("mock:a")
                    .bean(BeanMethodNameHeaderIssueTest.class, "bar")
                    .to("mock:b")
                    .bean(BeanMethodNameHeaderIssueTest.class)
                    .to("mock:c")
                    .setHeader(Exchange.BEAN_METHOD_NAME, constant("echo"))
                    .bean(BeanMethodNameHeaderIssueTest.class)
                    .to("mock:d");
            }
        };
    }

    public String foo() {
        return "foo";
    }

    public String bar() {
        return "bar";
    }

    public String echo(String body) {
        return body + " " + body;
    }

    @Handler
    public String doSomething(String body) {
        return "Bye " + body;
    }

}
