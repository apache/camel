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
package org.apache.camel.component.activemq;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Used to get an idea of what kind of performance can be expected from 
 * the journal.
 * 
 * @version $Revision$
 */
public class JournalRoutePerformance extends ContextTestSupport {

    AtomicLong produceCounter = new AtomicLong();
    AtomicLong consumeCounter = new AtomicLong();
    AtomicBoolean running = new AtomicBoolean(true);

    public void testPerformance() throws Exception {

        int payLoadSize = 1024;
        int concurrentProducers = 50;
        long delayBetweenSample = 1000;
        long perfTestDuration = 1000 * 60; // 1 min

        StringBuffer t = new StringBuffer();
        for (int i = 0; i < payLoadSize; i++) {
            t.append('a' + (i % 26));
        }
        final byte[] payload = t.toString().getBytes("UTF-8");

        for (int i = 0; i < concurrentProducers; i++) {
            Thread thread = new Thread("Producer: " + i) {
                @Override
                public void run() {
                    while (running.get()) {
                        template.sendBody("direct:in", payload);
                        produceCounter.incrementAndGet();
                    }
                }
            };
            thread.start();
        }

        long produceTotal = 0;
        long consumeTotal = 0;
        long start = System.currentTimeMillis();
        long end = start + perfTestDuration;
        while (System.currentTimeMillis() < end) {
            Thread.sleep(delayBetweenSample);
            long totalTime = System.currentTimeMillis() - start;
            long p = produceCounter.getAndSet(0);
            long c = consumeCounter.getAndSet(0);
            produceTotal += p;
            consumeTotal += c;
            System.out.println("Interval Produced " + stat(p, delayBetweenSample) + " m/s, Consumed " + stat(c, delayBetweenSample) + " m/s");
            System.out.println("Total Produced " + stat(produceTotal, totalTime) + " m/s, Consumed " + stat(consumeTotal, totalTime) + " m/s");
        }
        running.set(false);

    }

    private String stat(long pd, long delayBetweenSample) {
        return "" + (1.0 * pd / delayBetweenSample) * 1000.0;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("activemq.journal:target/perf-test");
                from("activemq.journal:target/perf-test").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        consumeCounter.incrementAndGet();
                    }
                });
            }
        };
    }
}
