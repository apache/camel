/**
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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class OnExceptionContinuePredicateTest extends OnExceptionContinueTest {

    private final AtomicInteger predicateInvoked = new AtomicInteger();
    private final AtomicInteger processorInvoked = new AtomicInteger();

    @Override
    public void testContinued() throws Exception {
        getMockEndpoint("mock:me").expectedMessageCount(1);

        super.testContinued();

        assertEquals(1, predicateInvoked.get());
        assertEquals(1, processorInvoked.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use a predicate instance
                Predicate predicate = new Predicate() {
                    @Override
                    public boolean matches(Exchange exchange) {
                        predicateInvoked.incrementAndGet();
                        return true;
                    }
                };

                // tell Camel to handle and continue when this exception is thrown
                onException(IllegalArgumentException.class)
                    .continued(predicate)
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            processorInvoked.incrementAndGet();
                        }
                    })
                    .to("mock:me");

                from("direct:start")
                    .to("mock:start")
                    .throwException(new IllegalArgumentException("Forced"))
                    .to("mock:result");
            }
        };
    }
}