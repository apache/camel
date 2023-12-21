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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoutePerformanceCountTest extends ContextTestSupport {

    private CountProcessor processor = new CountProcessor();
    private int size = 500;
    private String url = "direct:start";

    @Test
    public void testSendMessages() throws Exception {
        StopWatch watch = new StopWatch();

        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
        }
        assertEquals(size, processor.getCounter());

        long delta = watch.taken();
        log.info("RoutePerformanceCountTest: Sent: {} Took: {} ms", size, delta);
    }

    @Override
    protected boolean canRunOnThisPlatform() {
        String os = System.getProperty("os.name");
        // HP-UX is just to slow to run this test
        return !os.toLowerCase(Locale.ENGLISH).contains("hp-ux");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:a?level=OFF", "log:b?level=OFF", "direct:c");

                from("direct:c").choice().when().header("foo").process(processor).otherwise().process(processor).end();
            }
        };
    }

    private static class CountProcessor implements Processor {
        private AtomicInteger counter = new AtomicInteger();

        @Override
        public void process(Exchange exchange) throws Exception {
            counter.incrementAndGet();
        }

        public int getCounter() {
            return counter.intValue();
        }
    }
}
