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
package org.apache.camel.issues;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SplitWithCustomAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testSplitWithCustomAggregatorStrategy() throws Exception {
        int files = 10;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(files);
        // no duplicates should be received
        mock.expectsNoDuplicates(body());

        for (int i = 0; i < files; i++) {
            template.sendBody("direct:start", "");
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody().simple("<search><key>foo-${id}</key><key>bar-${id}</key><key>baz-${id}</key></search>").to("direct:splitInOut").to("mock:result");

                from("direct:splitInOut").setHeader("com.example.id").simple("${id}").split(xpath("/search/key"), new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            return newExchange;
                        }

                        String oldBody = oldExchange.getIn().getBody(String.class);
                        String newBody = newExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(oldBody + newBody);

                        return oldExchange;
                    }
                }).parallelProcessing().streaming().to("direct:processLine").end().transform().simple("<results>${in.body}</results>");

                from("direct:processLine").to("log:line").transform().simple("<index>${in.header.CamelSplitIndex}</index>${in.body}");
            }
        };
    }
}
