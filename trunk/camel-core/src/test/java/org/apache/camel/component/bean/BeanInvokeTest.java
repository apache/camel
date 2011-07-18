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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class BeanInvokeTest extends ContextTestSupport {

    public void testA() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:a");

        mock.expectedBodiesReceived("Hello World");
        template.sendBody("direct:a", "Hello World");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("");
        template.sendBody("direct:a", "");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();
        template.sendBody("direct:a", null);
        assertMockEndpointsSatisfied();
    }

    public void testB() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:b");

        mock.expectedBodiesReceived("Bye World");
        template.sendBody("direct:b", "Hello World");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();
        template.sendBody("direct:b", "");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();
        template.sendBody("direct:b", null);
        assertMockEndpointsSatisfied();
    }

    public void testC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:c");

        mock.expectedBodiesReceived("Hello World");
        template.sendBody("direct:c", "Hello World");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("");
        template.sendBody("direct:c", "");
        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();
        template.sendBody("direct:c", null);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").bean(BeanInvokeTest.class, "doSomething").to("mock:a");
                from("direct:b").bean(BeanInvokeTest.class, "changeSomething").to("mock:b");
                from("direct:c").bean(BeanInvokeTest.class, "doNothing").to("mock:c");
            }
        };
    }

    public String doSomething(String s) {
        return s;
    }

    public String changeSomething(String s) {
        if ("Hello World".equals(s)) {
            return "Bye World";
        }
        return null;
    }

    public void doNothing(String s) {
    }

}