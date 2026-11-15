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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A poison message (eagerLoadingOfProperties) goes to the error handler without redeliveries, as its payload cannot be
 * read again: the route is not run with the poison body.
 */
public class PoisonMessageRedeliveryTest extends JmsTestSupport {

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testPoisonMessageGoesToDeadLetterChannel() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("sjms:queue:PoisonMessageRedeliveryTest?objectMessageEnabled=true", new Poison());

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(0, getMockEndpoint("mock:step").getReceivedCounter());
        Exchange exchange = dead.getReceivedExchanges().get(0);
        assertTrue(exchange.getIn().getBody(String.class).startsWith("Poison JMS message payload: "));
        assertNotNull(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:PoisonMessageRedeliveryTest?eagerLoadingOfProperties=true&objectMessageEnabled=true")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:step");
            }
        };
    }

    public static class Poison implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Serial
        private void readObject(ObjectInputStream in) throws IOException {
            throw new IOException("poison!");
        }
    }
}
