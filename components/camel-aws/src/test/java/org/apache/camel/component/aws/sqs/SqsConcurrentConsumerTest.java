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
package org.apache.camel.component.aws.sqs;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.sqs.model.Message;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SqsConcurrentConsumerTest extends CamelTestSupport {
    private static final int NUM_CONCURRENT = 10;
    private static final int NUM_MESSAGES = 100;

    final Set<Long> threadNumbers = new HashSet<Long>();

    @Test
    public void consumeMessagesFromQueue() throws Exception {
        // simple test to make sure that concurrent consumers were used in the test

        NotifyBuilder notifier = new NotifyBuilder(context).whenCompleted(NUM_MESSAGES).create();
        assertTrue("We didn't process " + NUM_MESSAGES + " messages as we expected!", notifier.matches(5, TimeUnit.SECONDS));

        if (isPlatform("windows")) {
            // threading is different on windows
        } else {
            // usually we use all threads evenly but sometimes threads are reused so just test that 50%+ was used
            if (threadNumbers.size() < (NUM_CONCURRENT / 2)) {
                fail(String.format("We were expecting to have about half of %d numbers of concurrent consumers, but only found %d",
                        NUM_CONCURRENT, threadNumbers.size()));
            }
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        AmazonSQSClientMock client = new AmazonSQSClientMock();
        createDummyMessages(client, NUM_MESSAGES);
        reg.bind("client", client);
        return reg;
    }

    private void createDummyMessages(AmazonSQSClientMock client, int numMessages) {
        for (int counter = 0; counter < numMessages; counter++) {
            Message message = new Message();
            message.setBody("Message " + counter);
            message.setMD5OfBody("6a1559560f67c5e7a7d5d838bf0272ee");
            message.setMessageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
            message.setReceiptHandle("0NNAq8PwvXsyZkR6yu4nQ07FGxNmOBWi5");
            client.messages.add(message);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("aws-sqs://demo?concurrentConsumers=" + NUM_CONCURRENT + "&maxMessagesPerPoll=10&amazonSQSClient=#client")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                threadNumbers.add(Thread.currentThread().getId());
                            }
                        }).log("processed a new message!");
            }
        };
    }

}
