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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This is a manual test to run
 */
@Ignore("Manual test")
public class RouteContextProcessorTest extends ContextTestSupport {

    // Number of concurrent processing threads
    public static final int CONCURRENCY = 10;

    // Additional resequencer time-out above theoretical time-out
    public static final long SAFETY_TIMEOUT = 100;

    // Additional resequencer capacity above theoretical capacity
    public static final int SAFETY_CAPACITY = 10;

    // Resequencer time-out
    public static final long TIMEOUT = SAFETY_TIMEOUT + (RandomSleepProcessor.MAX_PROCESS_TIME - RandomSleepProcessor.MIN_PROCESS_TIME);

    // Resequencer capacity
    public static final int CAPACITY = SAFETY_CAPACITY + (int)(CONCURRENCY * TIMEOUT / RandomSleepProcessor.MIN_PROCESS_TIME);

    private static final int NUMBER_OF_MESSAGES = 10000;

    @Test
    public void testForkAndJoin() throws InterruptedException {
        // enable the other test method for manual testing
    }

    public void xxxTestForkAndJoin() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(NUMBER_OF_MESSAGES);

        ProducerTemplate template = context.createProducerTemplate();
        for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            template.sendBodyAndHeader("seda:fork", "Test Message: " + i, "seqnum", new Long(i));
        }

        long expectedTime = NUMBER_OF_MESSAGES * (RandomSleepProcessor.MAX_PROCESS_TIME + RandomSleepProcessor.MIN_PROCESS_TIME) / 2 / CONCURRENCY + TIMEOUT;
        Thread.sleep(expectedTime);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Processor myProcessor = new RandomSleepProcessor();
                from("seda:fork?concurrentConsumers=" + CONCURRENCY).process(myProcessor).to("seda:join");
                from("seda:join").resequence(header("seqnum")).stream().capacity(CAPACITY).timeout(TIMEOUT).to("mock:result");
            }
        };
    }

    /**
     * Simulation processor that sleeps a random time between MIN_PROCESS_TIME
     * and MAX_PROCESS_TIME milliseconds.
     */
    public static class RandomSleepProcessor implements Processor {
        public static final long MIN_PROCESS_TIME = 5;
        public static final long MAX_PROCESS_TIME = 50;

        @Override
        public void process(Exchange arg0) throws Exception {
            long processTime = (long)(MIN_PROCESS_TIME + Math.random() * (MAX_PROCESS_TIME - MIN_PROCESS_TIME));
            Thread.sleep(processTime);
        }
    }
}
