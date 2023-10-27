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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ErrorHandlerSuppressExceptionTest extends ContextTestSupport {

    @Test
    public void testSuppressException() throws Exception {
        Exchange out = template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        Assertions.assertTrue(out.isFailed());
        Exception t = out.getException();
        Assertions.assertNotNull(t);
        Assertions.assertEquals("Forced error during handling", t.getMessage());
        // only 1 suppressed to avoid the same exception being added multiple times
        Assertions.assertEquals(1, t.getSuppressed().length);
        Assertions.assertEquals("Root exception", t.getSuppressed()[0].getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).maximumRedeliveries(3).redeliveryDelay(0)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // throw a new exception while handling an exception
                                // this should not leak with the same exception being nested
                                // as suppressed exception
                                throw new IllegalArgumentException("Forced error during handling");
                            }
                        });

                from("direct:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new IOException("Root exception");
                            }
                        });
            }
        };
    }

}
