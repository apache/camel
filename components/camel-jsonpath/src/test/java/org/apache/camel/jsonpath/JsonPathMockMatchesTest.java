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
package org.apache.camel.jsonpath;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsonPathMockMatchesTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testMockMatches() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        // cheap
        mock.message(0).predicate().jsonpath("$.store.book[?(@.price < 10)]");
        // average
        mock.message(2).predicate().jsonpath("$.store.book[?(@.price < 30)]");
        // expensive
        mock.message(1).predicate().jsonpath("$.store.book[?(@.price > 30)]");

        template.sendBody("direct:start", new File("src/test/resources/cheap.json"));
        template.sendBody("direct:start", new File("src/test/resources/expensive.json"));
        template.sendBody("direct:start", new File("src/test/resources/average.json"));

        mock.assertIsSatisfied();
    }

    @Test
    public void testMockNotMatches() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        // cheap
        mock.message(0).predicate().jsonpath("$.store.book[?(@.price < 10)]");
        // average
        mock.message(1).predicate().jsonpath("$.store.book[?(@.price < 30)]");
        // expensive
        mock.message(2).predicate().jsonpath("$.store.book[?(@.price > 30)]");

        template.sendBody("direct:start", new File("src/test/resources/cheap.json"));
        template.sendBody("direct:start", new File("src/test/resources/expensive.json"));
        template.sendBody("direct:start", new File("src/test/resources/average.json"));

        mock.assertIsNotSatisfied();
    }

}
