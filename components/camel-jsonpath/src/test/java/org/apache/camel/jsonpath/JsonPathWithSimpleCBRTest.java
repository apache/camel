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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsonPathWithSimpleCBRTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when().jsonpath("$.store.book[?(@.price < ${header.cheap})]")
                            .to("mock:cheap")
                        .when().jsonpath("$.store.book[?(@.price < ${header.average})]")
                            .to("mock:average")
                        .otherwise()
                            .to("mock:expensive");
            }
        };
    }
    
    @Test
    public void testCheap() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(1);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        fluentTemplate.withHeader("cheap", 10).withHeader("average", 30).withBody(new File("src/test/resources/cheap.json"))
                .to("direct:start").send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAverage() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(1);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        fluentTemplate.withHeader("cheap", 10).withHeader("average", 30).withBody(new File("src/test/resources/average.json"))
                .to("direct:start").send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExpensive() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(1);

        fluentTemplate.withHeader("cheap", 10).withHeader("average", 30).withBody(new File("src/test/resources/expensive.json"))
                .to("direct:start").send();

        assertMockEndpointsSatisfied();
    }

}
