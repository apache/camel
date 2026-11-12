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

import java.io.InputStream;
import java.util.Random;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A received StreamMessage is mapped to an InputStream over its bytes, as in the jms component.
 */
public class StreamMessageConsumerTest extends JmsTestSupport {

    private volatile Object body;
    private volatile byte[] received;

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testStreamMessageIsInputStream() throws Exception {
        // larger than one chunk of the producer, so the message holds several byte[] fields
        byte[] payload = new byte[300 * 1024];
        new Random(42).nextBytes(payload);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("sjms:queue:StreamMessageConsumerTest?jmsMessageType=Stream", payload);

        MockEndpoint.assertIsSatisfied(context);
        assertInstanceOf(InputStream.class, body);
        assertArrayEquals(payload, received);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:StreamMessageConsumerTest")
                        .streamCache("false")
                        .process(exchange -> {
                            body = exchange.getIn().getBody();
                            received = exchange.getIn().getBody(InputStream.class).readAllBytes();
                        })
                        .to("mock:result");
            }
        };
    }
}
