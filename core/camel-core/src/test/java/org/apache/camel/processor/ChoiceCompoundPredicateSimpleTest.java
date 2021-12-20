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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.PredicateBuilder.or;

public class ChoiceCompoundPredicateSimpleTest extends ContextTestSupport {

    @Test
    public void testNull() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(2);
        getMockEndpoint("mock:data").expectedMessageCount(0);

        template.sendBody("direct:simple", null);
        template.sendBody("direct:or", null);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmpty() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(2);
        getMockEndpoint("mock:data").expectedMessageCount(0);

        template.sendBody("direct:simple", new ArrayList<>());
        template.sendBody("direct:or", new ArrayList<>());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyOr() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(1);
        getMockEndpoint("mock:data").expectedMessageCount(0);

        template.sendBody("direct:or", new ArrayList<>());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptySimple() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(1);
        getMockEndpoint("mock:data").expectedMessageCount(0);

        template.sendBody("direct:simple", new ArrayList<>());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testData() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(0);
        getMockEndpoint("mock:data").expectedMessageCount(2);

        List<String> list = new ArrayList<>();
        list.add("Hello Camel");
        template.sendBody("direct:simple", list);
        template.sendBody("direct:or", list);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDataOr() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(0);
        getMockEndpoint("mock:data").expectedMessageCount(1);

        List<String> list = new ArrayList<>();
        list.add("Hello Camel");
        template.sendBody("direct:or", list);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDataSimple() throws Exception {
        getMockEndpoint("mock:empty").expectedMessageCount(0);
        getMockEndpoint("mock:data").expectedMessageCount(1);

        List<String> list = new ArrayList<>();
        list.add("Hello Camel");
        template.sendBody("direct:simple", list);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:or")
                    .choice()
                        .when(or(body().isNull(), simple("${body.size()} == 0")))
                        .to("mock:empty")
                    .otherwise()
                        .to("mock:data")
                    .end();

                from("direct:simple")
                    .choice()
                        .when(simple("${body} == null || ${body.size()} == 0"))
                        .to("mock:empty")
                    .otherwise()
                        .to("mock:data")
                    .end();
            }
        };
    }

}
