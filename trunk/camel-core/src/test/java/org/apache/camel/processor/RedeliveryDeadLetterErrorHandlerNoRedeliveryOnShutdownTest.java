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
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;

public class RedeliveryDeadLetterErrorHandlerNoRedeliveryOnShutdownTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    public void testRedeliveryErrorHandlerNoRedeliveryOnShutdown() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:deadLetter").expectedMessageCount(1);
        getMockEndpoint("mock:deadLetter").setResultWaitTime(25000);

        template.sendBody("seda:foo", "Hello World");

        getMockEndpoint("mock:foo").assertIsSatisfied();

        // should not take long to stop the route
        StopWatch watch = new StopWatch();
        // sleep 3 seconds to do some redeliveries before we stop
        Thread.sleep(3000);
        log.info("==== stopping route foo ====");
        context.stopRoute("foo");
        watch.stop();

        getMockEndpoint("mock:deadLetter").assertIsSatisfied();

        log.info("OnRedelivery processor counter {}", counter.get());

        assertTrue("Should stop route faster, was " + watch.taken(), watch.taken() < 7000);
        assertTrue("Redelivery counter should be >= 2 and < 12, was: " + counter.get(), counter.get() >= 2 && counter.get() < 12);
    }

    private final class MyRedeliverProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            counter.incrementAndGet();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:deadLetter")
                        .allowRedeliveryWhileStopping(false)
                        .onRedelivery(new MyRedeliverProcessor())
                        .maximumRedeliveries(20).redeliveryDelay(1000).retryAttemptedLogLevel(LoggingLevel.INFO));

                from("seda:foo").routeId("foo")
                    .to("mock:foo")
                    .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
