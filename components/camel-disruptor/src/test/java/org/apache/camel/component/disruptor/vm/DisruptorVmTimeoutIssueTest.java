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
package org.apache.camel.component.disruptor.vm;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DisruptorVmTimeoutIssueTest extends AbstractVmTestSupport {

    @Test
    public void testDisruptorVmTimeoutWithAnotherDisruptorVm() throws Exception {
        try {
            template2.requestBody("disruptor-vm:start1?timeout=4000", "Hello");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class,
                    e.getCause());
            assertEquals(2000, cause.getTimeout());
        }
    }

    @Test
    public void testDisruptorVmTimeoutWithProcessor() throws Exception {
        try {
            template2.requestBody("disruptor-vm:start2?timeout=4000", "Hello");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class,
                    e.getCause());
            assertEquals(2000, cause.getTimeout());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor-vm:end")
                        .delay(3000).transform().constant("Bye World");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("disruptor-vm:start1?timeout=4000")
                        .to("log:AFTER_START1")
                        .to("disruptor-vm:end?timeout=2000")
                        .to("log:AFTER_END");

                from("disruptor-vm:start2?timeout=4000")
                        .to("log:AFTER_START2")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // this exception will trigger to stop asap
                                throw new ExchangeTimedOutException(exchange, 2000);
                            }
                        })
                        .to("log:AFTER_PROCESSOR");
            }
        };
    }
}