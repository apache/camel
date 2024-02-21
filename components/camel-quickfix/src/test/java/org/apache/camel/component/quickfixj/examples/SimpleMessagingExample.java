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
package org.apache.camel.component.quickfixj.examples;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.component.quickfixj.TestSupport;
import org.apache.camel.component.quickfixj.examples.transform.QuickfixjMessageJsonPrinter;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.field.MsgType;
import quickfix.fix42.Email;

/**
 * This example demonstrates several features of the QuickFIX/J component. It uses QFJ session events to synchronize
 * application behavior (e.g., Session logon).
 */
public class SimpleMessagingExample {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleMessagingExample.class);

    public static void main(String[] args) throws Exception {
        new SimpleMessagingExample().sendMessage();
    }

    public void sendMessage() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();

        final CountDownLatch logonLatch = new CountDownLatch(2);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);

        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Release latch when session logon events are received
                // We expect two events, one for the trader session and one for the market session
                from("quickfix:examples/inprocess.qf.cfg")
                        .filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                        .bean(new CountDownLatchDecrementer("logon", logonLatch));

                // For all received messages, print the JSON-formatted message to stdout
                from("quickfix:examples/inprocess.qf.cfg").filter(PredicateBuilder.or(
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AdminMessageReceived),
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived)))
                        .bean(new QuickfixjMessageJsonPrinter());

                // If the market session receives an email then release the latch
                from("quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:MARKET->TRADER")
                        .filter(header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.EMAIL))
                        .bean(new CountDownLatchDecrementer("message", receivedMessageLatch));
            }
        };

        context.addRoutes(routes);

        LOG.info("Starting Camel context");
        context.start();

        if (!logonLatch.await(5L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Logon did not succeed");
        }

        String marketUri = "quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:TRADER->MARKET";
        Producer producer = context.getEndpoint(marketUri).createProducer();

        Email email = TestSupport.createEmailMessage("Example");
        Exchange exchange = producer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        producer.process(exchange);

        if (!receivedMessageLatch.await(5L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Message did not reach market");
        }

        LOG.info("Message received, shutting down Camel context");

        context.stop();

        LOG.info("Example complete");
    }
}
