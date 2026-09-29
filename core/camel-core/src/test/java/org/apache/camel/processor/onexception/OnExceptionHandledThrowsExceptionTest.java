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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OnExceptionHandledThrowsExceptionTest extends ContextTestSupport {

    @Test
    public void testHandled() throws Exception {
        // the handled predicate fails, which is regarded as not handled, so the onException is still processed
        // and the exchange fails with the original exception (with the exception from the predicate as suppressed)
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (Exception e) {
            IOException io = assertIsInstanceOf(IOException.class, e.getCause());
            assertEquals("Forced", io.getMessage());
            assertEquals(1, io.getSuppressed().length);
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, io.getSuppressed()[0]);
            assertEquals("Another Forced", iae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class)
                        .handled(e -> {
                            throw new IllegalArgumentException("Another Forced");
                        }).to("log:foo?showAll=true").to("mock:handled");

                from("direct:start").throwException(new IOException("Forced"));
            }
        };
    }
}
