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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A POJO aggregation strategy with a String parameter on a message without a body (a timer message) says so.
 */
public class AggregationStrategyBeanAdapterNullBodyTest extends ContextTestSupport {

    @Test
    public void testNullBodyIsExplained() throws Exception {
        // the first message is kept as is (no old exchange to aggregate with), the second one calls the POJO
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
        Throwable e = getMockEndpoint("mock:dead").getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT,
                Exception.class);
        while (e != null && !(e instanceof RuntimeCamelException)) {
            e = e.getCause();
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("append has a parameter of type String"), e.getMessage());
        assertTrue(e.getMessage().contains("the message has no body (null)"), e.getMessage());
        assertTrue(e.getMessage().contains("setBody"), e.getMessage());
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").aggregate(constant(true), AggregationStrategies.bean(new MyBodyAppender(), "append"))
                        .completionSize(2).to("mock:result");
            }
        };
    }

    public static final class MyBodyAppender {
        public String append(String existing, String next) {
            return existing + next;
        }
    }
}
