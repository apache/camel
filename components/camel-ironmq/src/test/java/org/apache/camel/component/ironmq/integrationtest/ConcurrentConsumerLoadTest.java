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
package org.apache.camel.component.ironmq.integrationtest;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ironmq.IronMQConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own projectId and token!")
public class ConcurrentConsumerLoadTest extends CamelTestSupport {
    private static final String IRONMQCLOUD = "https://mq-aws-eu-west-1-1.iron.io";
    private static final int NO_OF_MESSAGES = 50000;
    private static final String BATCH_DELETE = "true";
    private static final int CONCURRENT_CONSUMERS = 20;
    private static final String PAYLOAD = "{some:text, number:#}";

    // replace with your project id
    private final String projectId = "myIronMQproject";
    // replace with your token
    private final String token = "myIronMQToken";
    // replace with your test queue name
    private final String ironmqQueue = "testqueue";

    private final String ironMQEndpoint = "ironmq:" + ironmqQueue + "?projectId=" + projectId + "&token=" + token + "&maxMessagesPerPoll=100&wait=30&ironMQCloud=" + IRONMQCLOUD
                                          + "&concurrentConsumers=" + CONCURRENT_CONSUMERS + "&batchDelete=" + BATCH_DELETE;
    private final String sedaEndpoint = "seda:push?concurrentConsumers=" + CONCURRENT_CONSUMERS;

    @Before
    public void prepareQueue() throws InterruptedException {
        // make sure the queue is empty before test
        template.sendBodyAndHeader(ironMQEndpoint, null, IronMQConstants.OPERATION, IronMQConstants.CLEARQUEUE);
        long start = System.currentTimeMillis();
        int noOfBlocks = 0;
        ArrayList<String> list = new ArrayList<>();
        for (int i = 1; i <= NO_OF_MESSAGES; i++) {
            String payloadToSend = PAYLOAD.replace("#", "" + i);
            list.add(payloadToSend);
            if (i % 100 == 0) {
                noOfBlocks++;
                System.out.println("sending blok " + noOfBlocks);
                template.sendBody(sedaEndpoint, list.toArray(new String[0]));
                list.clear();
            }
        }
        MockEndpoint mockEndpoint = getMockEndpoint("mock:iron");
        while (mockEndpoint.getReceivedCounter() != noOfBlocks) {
            log.info("Waiting for queue to fill up. Current size is " + mockEndpoint.getReceivedCounter() * 100);
            Thread.sleep(1000);
        }
        long delta = System.currentTimeMillis() - start;
        int seconds = (int)delta / 1000;
        int msgPrSec = NO_OF_MESSAGES / seconds;
        log.info("IronMQPerformanceTest: Took: " + seconds + " seconds to produce " + NO_OF_MESSAGES + " messages. Which is " + msgPrSec + " messages pr. second");
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        long start = System.currentTimeMillis();
        context.getRouteController().startRoute("iron");
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(NO_OF_MESSAGES);
        assertMockEndpointsSatisfied(4, TimeUnit.MINUTES);
        long delta = System.currentTimeMillis() - start;
        int seconds = (int)delta / 1000;
        int msgPrSec = NO_OF_MESSAGES / seconds;
        log.info("IronmqPerformanceTest: Took: " + seconds + " seconds to consume " + NO_OF_MESSAGES + " messages. Which is " + msgPrSec + " messages pr. second");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ironMQEndpoint).id("iron").autoStartup(false).to("mock:result");
                from(sedaEndpoint).to(ironMQEndpoint).to("mock:iron");
            }
        };
    }

}
