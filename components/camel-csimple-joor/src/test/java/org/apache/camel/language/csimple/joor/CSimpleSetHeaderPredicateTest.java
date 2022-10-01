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
package org.apache.camel.language.csimple.joor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class CSimpleSetHeaderPredicateTest extends CamelTestSupport {

    @Test
    public void testSetHeaderPredicateFalse() throws Exception {
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", false);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetHeaderPredicateTrue() throws Exception {
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", true);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "Camel");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOther() throws Exception {
        getMockEndpoint("mock:other").expectedHeaderReceived("param1", "hello");
        getMockEndpoint("mock:other").expectedHeaderReceived("param2", true);

        template.sendBody("direct:other", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setHeader("bar").csimple("${header.foo} == 'Camel'", boolean.class).to("mock:result");

                from("direct:other").setHeader("param1", constant("hello")).log("param1 = ${header.param1}").setHeader("param2")
                        .csimple("${header.param1} == 'hello'", Boolean.class).log("param2 = ${header.param2}")
                        .to("mock:other");
            }
        };
    }

}
