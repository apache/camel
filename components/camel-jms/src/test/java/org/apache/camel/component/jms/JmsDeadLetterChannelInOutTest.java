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
package org.apache.camel.component.jms;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(20)
public class JmsDeadLetterChannelInOutTest extends AbstractPersistentJMSTest {

    @Test
    public void testJmsDLCInOut() {
        final CompletableFuture<Exchange> future = template.asyncSend("direct:start", exchange -> {
            // use InOut
            exchange.setPattern(ExchangePattern.InOut);
            exchange.getIn().setBody("Hello World");
        });

        Exchange out = assertDoesNotThrow(() -> future.get());
        assertNotNull(out);

        // should be in DLQ
        Object dead = consumer.receiveBody("activemq:queue:JmsDeadLetterChannelInOutTest.error", 5000);
        assertEquals("Hello World", dead);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("activemq:queue:JmsDeadLetterChannelInOutTest.error"));

                from("direct:start").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
