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
package org.apache.camel.component.disruptor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class DisruptorInOutChainedTimeoutTest extends CamelTestSupport {
    @Test
    public void testDisruptorInOutChainedTimeout() throws Exception {
        // time timeout after 2 sec should trigger a immediately reply
        final StopWatch watch = new StopWatch();
        try {
            template.requestBody("disruptor:a?timeout=5000", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            final ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class,
                    e.getCause());
            assertEquals(2000, cause.getTimeout());
        }
        final long delta = watch.taken();

        assertTrue("Should be faster than 4000 millis, was: " + delta, delta < 4000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("disruptor:a").to("mock:a")
                        // this timeout will trigger an exception to occur
                        .to("disruptor:b?timeout=2000").to("mock:a2");

                from("disruptor:b").to("mock:b").delay(3000).transform().constant("Bye World");
            }
        };
    }
}
