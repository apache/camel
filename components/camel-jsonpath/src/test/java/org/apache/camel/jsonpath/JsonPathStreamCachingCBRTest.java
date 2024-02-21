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
import java.io.FileInputStream;

import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsonPathStreamCachingCBRTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getStreamCachingStrategy().setSpoolDirectory("target/tmp");
                context.getStreamCachingStrategy().setSpoolThreshold(-1);

                from("direct:start")
                        .streamCaching()
                        .choice()
                        .when().jsonpath("$.store.book[?(@.price < 10)]")
                        .to("mock:cheap")
                        .when().jsonpath("$.store.book[?(@.price < 30)]")
                        .to("mock:average")
                        .otherwise()
                        .to("mock:expensive");

                from("direct:bicycle")
                        .streamCaching()
                        .choice()
                        .when().method(new BeanPredicate())
                        .to("mock:cheap")
                        .otherwise()
                        .to("mock:expensive");

                from("direct:bicycle2")
                        .streamCaching()
                        .choice()
                        .when(PredicateBuilder.isLessThan(
                                ExpressionBuilder.languageExpression("jsonpath", "$.store.bicycle.price"),
                                ExpressionBuilder.constantExpression(100)))
                        .to("mock:cheap")
                        .otherwise()
                        .to("mock:expensive");
            }
        };
    }

    public static class BeanPredicate {
        public boolean checkPrice(@JsonPath("$.store.bicycle.price") double price) {
            return price < 100;
        }
    }

    @Test
    public void testCheapBicycle() throws Exception {
        sendMessageToBicycleRoute("direct:bicycle");
        MockEndpoint.resetMocks(context);
        sendMessageToBicycleRoute("direct:bicycle2");
    }

    private void sendMessageToBicycleRoute(String startPoint) throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(1);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        template.sendBody(startPoint, new FileInputStream(new File("src/test/resources/cheap.json")));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCheap() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(1);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        template.sendBody("direct:start", new FileInputStream(new File("src/test/resources/cheap.json")));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testAverage() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(1);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        template.sendBody("direct:start", new FileInputStream(new File("src/test/resources/average.json")));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExpensive() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(1);

        template.sendBody("direct:start", new FileInputStream(new File("src/test/resources/expensive.json")));

        MockEndpoint.assertIsSatisfied(context);
    }

}
