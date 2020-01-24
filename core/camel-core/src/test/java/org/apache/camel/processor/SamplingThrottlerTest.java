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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SamplingThrottlerTest extends ContextTestSupport {

    @Test
    public void testSamplingFromExchangeStream() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(15).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.setResultWaitTime(3000);

        List<Exchange> sentExchanges = new ArrayList<>();
        sendExchangesThroughDroppingThrottler(sentExchanges, 15);

        notify.matchesMockWaitTime();
        mock.assertIsSatisfied();

        validateDroppedExchanges(sentExchanges, mock.getReceivedCounter());
    }

    @Test
    public void testBurstySampling() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.setResultWaitTime(3000);

        List<Exchange> sentExchanges = new ArrayList<>();

        // send a burst of 5 exchanges, expecting only one to get through
        sendExchangesThroughDroppingThrottler(sentExchanges, 5);
        // sleep through a complete period
        Thread.sleep(1100);
        // send another 5 now
        sendExchangesThroughDroppingThrottler(sentExchanges, 5);

        notify.matchesMockWaitTime();
        mock.assertIsSatisfied();

        validateDroppedExchanges(sentExchanges, mock.getReceivedCounter());
    }

    @Test
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);
        mock.setResultWaitTime(4000);

        final List<Exchange> sentExchanges = Collections.synchronizedList(new ArrayList<Exchange>());
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        sendExchangesThroughDroppingThrottler(sentExchanges, 35);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
        }

        mock.assertIsSatisfied();
        executor.shutdownNow();
    }

    @Test
    public void testSamplingUsingMessageFrequency() throws Exception {
        long totalMessages = 100;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(10);
        mock.setResultWaitTime(100);

        for (int i = 0; i < totalMessages; i++) {
            template.sendBody("direct:sample-messageFrequency", "<message>" + i + "</message>");
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testSamplingUsingMessageFrequencyViaDSL() throws Exception {
        long totalMessages = 50;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(10);
        mock.setResultWaitTime(100);

        for (int i = 0; i < totalMessages; i++) {
            template.sendBody("direct:sample-messageFrequency-via-dsl", "<message>" + i + "</message>");
        }

        mock.assertIsSatisfied();
    }

    private void sendExchangesThroughDroppingThrottler(List<Exchange> sentExchanges, int messages) throws Exception {
        ProducerTemplate myTemplate = context.createProducerTemplate();

        DirectEndpoint targetEndpoint = resolveMandatoryEndpoint("direct:sample", DirectEndpoint.class);
        for (int i = 0; i < messages; i++) {
            Exchange e = targetEndpoint.createExchange();
            e.getIn().setBody("<message>" + i + "</message>");
            // only send if we are still started
            if (context.getStatus().isStarted()) {
                myTemplate.send(targetEndpoint, e);
                sentExchanges.add(e);
                Thread.sleep(100);
            }
        }
        myTemplate.stop();
    }

    private void validateDroppedExchanges(List<Exchange> sentExchanges, int expectedNotDroppedCount) {
        int notDropped = 0;
        for (Exchange e : sentExchanges) {
            boolean stopped = e.isRouteStop();
            if (!stopped) {
                notDropped++;
            }
        }
        assertEquals(expectedNotDroppedCount, notDropped);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:sample").sample().to("mock:result");

                from("direct:sample-configured").sample(1, TimeUnit.SECONDS).to("mock:result");

                from("direct:sample-configured-via-dsl").sample().samplePeriod(1).timeUnits(TimeUnit.SECONDS).to("mock:result");

                from("direct:sample-messageFrequency").sample(10).to("mock:result");

                from("direct:sample-messageFrequency-via-dsl").sample().sampleMessageFrequency(5).to("mock:result");

                // END SNIPPET: e1
            }
        };
    }
}
