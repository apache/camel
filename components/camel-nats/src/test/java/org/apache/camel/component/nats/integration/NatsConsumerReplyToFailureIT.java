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
package org.apache.camel.component.nats.integration;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NATS has no error reply: when the exchange of an InOut consumer fails, the consumer must not answer with the body of
 * the failed exchange as if it succeeded, so the requester times out. A failure handled by the route is answered.
 */
public class NatsConsumerReplyToFailureIT extends NatsITSupport {

    @BeforeEach
    public void waitForConsumers() {
        waitForNatsConsumers(2);
    }

    @Test
    public void testNoReplyWhenExchangeFailed() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("nats:failing?requestTimeout=1000", "World", String.class));

        ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        assertEquals(1000, cause.getTimeout());
    }

    @Test
    public void testReplyWhenFailureHandled() {
        String reply = template.requestBody("nats:handled?requestTimeout=5000", "World", String.class);

        assertEquals("Handled Forced", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:failing?exchangePattern=InOut")
                        .throwException(new IllegalArgumentException("Forced"));

                from("nats:handled?exchangePattern=InOut")
                        .onException(IllegalArgumentException.class).handled(true)
                            .setBody(simple("Handled ${exception.message}"))
                        .end()
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
