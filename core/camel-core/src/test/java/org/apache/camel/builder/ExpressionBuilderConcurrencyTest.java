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

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExpressionBuilderConcurrencyTest extends ContextTestSupport {

    @Test
    public void testConcatExpressionConcurrency() throws Exception {
        MockEndpoint mockWithFailure = getMockEndpoint("mock:result");
        mockWithFailure.expectedMinimumMessageCount(100);
        mockWithFailure.assertIsSatisfied();
        List<Exchange> exchanges = mockWithFailure.getExchanges();
        exchanges.stream()
                .forEach(exchange -> Assertions
                        .assertEquals(
                                "This is a test a with startLabel: `Document` endLabel: `Document` and label: `ALabel`",
                                exchange.getMessage().getHeader("#CustomHeader", String.class)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            final Map<String, String> body = Map.of("label", "ALabel", "startLabel", "Document", "endLabel", "Document");
            final String simpleTemplate
                    = "This is a test a with startLabel: `${body.get('startLabel')}` endLabel: `${body.get('endLabel')}` and label: `${body.get('label')}`";

            @Override
            public void configure() throws Exception {

                from("timer://test-timer3?fixedRate=true&period=10&delay=1")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getMessage().setBody(body);
                                exchange.getMessage().setHeader("#CustomHeader", resolveTemplate(simpleTemplate, exchange));
                            }
                        })
                        .to("mock:result");

                from("timer://test-timer4?fixedRate=true&period=10&delay=1")

                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getMessage().setBody(body);
                                exchange.getMessage().setHeader("#CustomHeader", resolveTemplate(simpleTemplate, exchange));
                            }
                        })
                        .to("mock:result");

                from("timer://test-timer5?fixedRate=true&period=10&delay=1")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getMessage().setBody(body);
                                exchange.getMessage().setHeader("#CustomHeader", resolveTemplate(simpleTemplate, exchange));
                            }
                        })
                        .to("mock:result");
            }

        };
    }

    public String resolveTemplate(String template, Exchange exchange) {
        var simpleExpression = new SimpleExpression();
        simpleExpression.setExpression(template);
        return simpleExpression.evaluate(exchange, String.class);
    }

}
