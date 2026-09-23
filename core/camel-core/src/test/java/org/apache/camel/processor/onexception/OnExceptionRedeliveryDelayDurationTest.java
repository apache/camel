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
package org.apache.camel.processor.onexception;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicy.RedeliveryOption;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The redelivery delays on onException are durations, the same as on the error handler.
 */
public class OnExceptionRedeliveryDelayDurationTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    public void testDurationRedeliveryDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        assertEquals(3, attempts.get());
        Exception cause = mock.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Forced", cause.getMessage());
    }

    @Test
    public void testDurationValues() {
        Map<RedeliveryOption, String> options = new EnumMap<>(RedeliveryOption.class);
        options.put(RedeliveryOption.redeliveryDelay, "5s");
        options.put(RedeliveryOption.maximumRedeliveryDelay, "1m30s");
        ExceptionPolicy policy = new ExceptionPolicy(
                null, null, false, false, false, null, null, null, null, null, null, options,
                List.of(IllegalArgumentException.class.getName()));

        RedeliveryPolicy answer = policy.createRedeliveryPolicy(context, new RedeliveryPolicy());
        assertEquals(5000, answer.getRedeliveryDelay());
        assertEquals(90000, answer.getMaximumRedeliveryDelay());

        // plain numbers are still milliseconds
        options.put(RedeliveryOption.redeliveryDelay, "250");
        options.put(RedeliveryOption.maximumRedeliveryDelay, "1000");
        answer = policy.createRedeliveryPolicy(context, new RedeliveryPolicy());
        assertEquals(250, answer.getRedeliveryDelay());
        assertEquals(1000, answer.getMaximumRedeliveryDelay());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class)
                        .maximumRedeliveries(2).redeliveryDelay("10ms").maximumRedeliveryDelay("1s")
                        .handled(true)
                        .to("mock:error");

                from("direct:start")
                        .process(e -> {
                            attempts.incrementAndGet();
                            throw new IllegalArgumentException("Forced");
                        });
            }
        };
    }
}
