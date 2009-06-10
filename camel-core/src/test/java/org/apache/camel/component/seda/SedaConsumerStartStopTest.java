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
package org.apache.camel.component.seda;

import java.util.Random;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit test to verify that we can stop and start a seda consumer while a producer
 * continues to send messages to it.
 */
public class SedaConsumerStartStopTest extends ContextTestSupport {

    private static final String SEDA_QUEUE_CONSUMERS_5 = "seda:queue?concurrentConsumers=5";
    private PollingConsumer consumer;

    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (consumer != null) {
            consumer.stop();
        }
    }

    private void sendMessagesToQueue() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.afterPropertiesSet();
        executor.execute(new Runnable() {
            public void run() {
                for (int i = 0; i < 10; i++) {
                    // when this delay is removed, the seda endpoint has ordering issues
                    try {
                        // do some random sleep to simulate spread in user activity
                        // range is 5-15
                        Thread.sleep(new Random().nextInt(10) + 5);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    template.sendBody("seda:queue", i);
                }
            }
        });
    }

    public void initRoute() throws Exception {
        Endpoint endpoint = context.getEndpoint("seda:queue");
        consumer = endpoint.createPollingConsumer();
    }

    public void testStartStopConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        mock.expectsAscending(body());

        initRoute();
        // will send messages to the queue in another thread simulation a producer
        sendMessagesToQueue();

        consumer.start();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                // stop while sending, and then start again to pickup what is left in the queue
                consumer.stop();
                Thread.sleep(500);
                consumer.start();
            }
            // use 1000 as timeout otherwise we might get null if the consumer hasn't been started again
            Exchange exchange = consumer.receive(1000);
            template.send("mock:result", exchange);
        }

        assertMockEndpointsSatisfied();
    }
    
    public void testConcurrentConsumers() throws Exception {
        context.addRoutes(new RouteBuilder(context) {
        
            @Override
            public void configure() throws Exception {
                from(SEDA_QUEUE_CONSUMERS_5).delay(500).to("mock:result");
        
            }
        });
        context.start();
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        
        for (int i = 0; i < 10; i++) {
            sendBody(SEDA_QUEUE_CONSUMERS_5, i);
        }
        
        Thread.sleep(800);
        assertEquals(5, mock.getReceivedCounter());
        
        Thread.sleep(700);
        assertEquals(10, mock.getReceivedCounter());
    }
}
