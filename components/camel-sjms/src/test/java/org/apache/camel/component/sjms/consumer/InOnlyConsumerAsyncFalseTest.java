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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests message ordering when using a single synchronous JMS consumer thread with asyncConsumer disabled.
 */
public class InOnlyConsumerAsyncFalseTest extends JmsTestSupport {

    private static final String SJMS_QUEUE_NAME = "sjms:queue:in.only.consumer.synch.InOnlyConsumerAsyncFalseTest";
    private static final String MOCK_RESULT = "mock:result";
    private static String beforeThreadName;
    private static String afterThreadName;

    @Test
    public void testInOnlyConsumerAsyncFalse() throws Exception {
        getMockEndpoint(MOCK_RESULT).expectedBodiesReceived("Hello Camel", "Hello World");

        template.sendBody(SJMS_QUEUE_NAME, "Hello Camel");
        template.sendBody(SJMS_QUEUE_NAME, "Hello World");

        // We expect message to be received in the same order as they were sent
        // despite delaying the processing of the first message,
        // as asyncConsumer is disabled and there is only one consumer thread
        // processing messages sequentially.
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(beforeThreadName, afterThreadName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(SJMS_QUEUE_NAME)
                        .to("log:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                                if (exchange.getIn().getBody(String.class).equals("Hello Camel")) {
                                    // delay processing of the message
                                    Thread.sleep(2000);
                                }
                            }
                        }).process(new Processor() {
                            public void process(Exchange exchange) {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        }).to("log:after").to(MOCK_RESULT);
            }
        };
    }
}
