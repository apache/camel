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
package org.apache.camel.jsonpath.easypredicate;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Easy predicates with {@code <=} and {@code >=} include the boundary, and a quoted value may contain an operator word.
 */
class EasyJsonPathComparisonOperatorsTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // cheap.json has one book of price 8.95
                from("direct:atMost")
                        .choice()
                        .when().jsonpath("store.book.price <= 8.95").to("mock:match")
                        .otherwise().to("mock:other");
                from("direct:atLeast")
                        .choice()
                        .when().jsonpath("$.store.book.price >= 8.95").to("mock:match")
                        .otherwise().to("mock:other");
                from("direct:title")
                        .choice()
                        .when().jsonpath("store.book.title == 'Java in Action'").to("mock:match")
                        .otherwise().to("mock:other");
            }
        };
    }

    @Test
    void lessThanOrEqualIncludesTheBoundary() throws Exception {
        send("direct:atMost", new File("src/test/resources/cheap.json"), 1);
    }

    @Test
    void greaterThanOrEqualIncludesTheBoundary() throws Exception {
        send("direct:atLeast", new File("src/test/resources/cheap.json"), 1);
    }

    @Test
    void quotedValueWithAnOperatorWord() throws Exception {
        send("direct:title", "{\"store\":{\"book\":[{\"title\":\"Java in Action\",\"price\":10}]}}", 1);
    }

    @Test
    void quotedValueWithAnOperatorWordThatDoesNotMatch() throws Exception {
        send("direct:title", "{\"store\":{\"book\":[{\"title\":\"Java in Practice\",\"price\":10}]}}", 0);
    }

    private void send(String uri, Object body, int matches) throws Exception {
        MockEndpoint match = getMockEndpoint("mock:match");
        MockEndpoint other = getMockEndpoint("mock:other");
        match.expectedMessageCount(matches);
        other.expectedMessageCount(1 - matches);
        template.sendBody(uri, body);
        MockEndpoint.assertIsSatisfied(context);
    }
}
