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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultErrorHandlerOnPrepareTest extends ContextTestSupport {

    @Test
    public void testDefaultErrorHandlerOnPrepare() {
        Exchange out = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertNotNull(out);
        assertTrue(out.isFailed(), "Should be failed");
        assertIsInstanceOf(IllegalArgumentException.class, out.getException());
        assertEquals("Forced", out.getIn().getHeader("FailedBecause"));
        assertEquals("foo", out.getIn().getHeader("FailedAtRoute"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().onPrepareFailure(new MyPrepareProcessor()));

                from("direct:start")
                        .routeId("foo")
                        .log("Incoming ${body}").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    public static class MyPrepareProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {
            Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            exchange.getIn().setHeader("FailedBecause", cause.getMessage());
            exchange.getIn().setHeader("FailedAtRoute", exchange.getProperty(Exchange.FAILURE_ROUTE_ID, String.class));
        }
    }
}
