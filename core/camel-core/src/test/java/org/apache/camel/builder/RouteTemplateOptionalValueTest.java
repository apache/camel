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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateOptionalValueTest extends ContextTestSupport {

    @Test
    public void testOptionalProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "start")
                .parameter("myRetain", "1")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:result?retainFirst=1").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOptional() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "start2")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleOptionalNoneProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myMultiTemplate")
                .parameter("foo", "multi1")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:multi1", "Hello World");
        template.sendBody("direct:multi1", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testMultipleOptionalSomeProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myMultiTemplate")
                .parameter("foo", "multi2")
                .parameter("myRetain", "1")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:result?retainFirst=1").expectedMessageCount(2);

        template.sendBody("direct:multi2", "Hello World");
        template.sendBody("direct:multi2", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(1, getMockEndpoint("mock:result?retainFirst=1").getReceivedExchanges().size());
    }

    @Test
    public void testMultipleOptionalAllProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myMultiTemplate")
                .parameter("foo", "multi3")
                .parameter("myRetain", "1")
                .parameter("myRetainLast", "1")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:result?retainFirst=1&retainLast=1").expectedMessageCount(3);

        template.sendBody("direct:multi3", "Hello World");
        template.sendBody("direct:multi3", "Middle World");
        template.sendBody("direct:multi3", "Bye World");

        assertMockEndpointsSatisfied();

        // retainFirst=1 keeps first, retainLast=1 keeps last = 2 retained
        assertEquals(2, getMockEndpoint("mock:result?retainFirst=1&retainLast=1").getReceivedExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate").templateParameter("foo").templateOptionalParameter("myRetain")
                        .from("direct:{{foo}}")
                        .to("mock:result?retainFirst={{?myRetain}}");

                routeTemplate("myMultiTemplate").templateParameter("foo")
                        .templateOptionalParameter("myRetain")
                        .templateOptionalParameter("myRetainLast")
                        .from("direct:{{foo}}")
                        .to("mock:result?retainFirst={{?myRetain}}&retainLast={{?myRetainLast}}");
            }
        };
    }
}
