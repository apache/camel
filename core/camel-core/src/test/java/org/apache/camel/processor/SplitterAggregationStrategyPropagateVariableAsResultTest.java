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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SplitterAggregationStrategyPropagateVariableAsResultTest extends ContextTestSupport {

    @Test
    public void testSplitVariable() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();

        MockEndpoint result = getMockEndpoint("mock:result");
        Exchange e = result.getExchanges().get(0);
        Assertions.assertNotNull(e);

        Assertions.assertTrue(e.hasProperties());
        List<?> l = e.getProperty("REP_PROP", List.class);
        Assertions.assertNotNull(l);
        Assertions.assertEquals(3, l.size());
        Assertions.assertEquals("A", l.get(0));
        Assertions.assertEquals("B", l.get(1));
        Assertions.assertEquals("C", l.get(2));

        Assertions.assertTrue(e.getMessage().hasHeaders());
        l = e.getMessage().getHeader("REP_HEAD", List.class);
        Assertions.assertNotNull(l);
        Assertions.assertEquals(3, l.size());
        Assertions.assertEquals("A", l.get(0));
        Assertions.assertEquals("B", l.get(1));
        Assertions.assertEquals("C", l.get(2));

        Assertions.assertTrue(e.hasVariables());
        l = e.getVariable("REP_VAL", List.class);
        Assertions.assertNotNull(l);
        Assertions.assertEquals(3, l.size());
        Assertions.assertEquals("A", l.get(0));
        Assertions.assertEquals("B", l.get(1));
        Assertions.assertEquals("C", l.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .split(body().tokenize(","), new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                var newBody = newExchange.getMessage().getBody(String.class);

                                if (oldExchange == null) {
                                    List<String> bodies = new ArrayList<>();
                                    List<String> properties = new ArrayList<>();
                                    List<String> headers = new ArrayList<>();
                                    List<String> variables = new ArrayList<>();

                                    bodies.add(newBody);
                                    properties.add(newBody);
                                    headers.add(newBody);
                                    variables.add(newBody);

                                    newExchange.getMessage().setBody(bodies);
                                    newExchange.getMessage().setHeader("REP_HEAD", headers);
                                    newExchange.setProperty("REP_PROP", properties);
                                    newExchange.setVariable("REP_VAL", variables);

                                    return newExchange;
                                }

                                var bodies = oldExchange.getMessage().getBody(List.class);
                                var properties = oldExchange.getProperty("REP_PROP", List.class);
                                var headers = oldExchange.getMessage().getHeader("REP_HEAD", List.class);
                                var variable = oldExchange.getVariable("REP_VAL", List.class);

                                bodies.add(newBody);
                                properties.add(newBody);
                                headers.add(newBody);
                                variable.add(newBody);

                                return oldExchange;
                            }
                        })
                        .to("mock:split")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
