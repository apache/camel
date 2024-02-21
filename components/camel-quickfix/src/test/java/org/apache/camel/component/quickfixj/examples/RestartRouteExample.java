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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.TestSupport;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.field.MsgType;
import quickfix.fix42.Email;

/**
 * Stopping a route should stop engine if no longer in use. And starting the route should start engine again.
 */
public class RestartRouteExample {
    private static final Logger LOG = LoggerFactory.getLogger(RestartRouteExample.class);

    public static void main(String[] args) throws Exception {
        new RestartRouteExample().sendMessage();
    }

    public void sendMessage() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();

        final CountDownLatch receivedMessageLatch = new CountDownLatch(2);

        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() {
                from("quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:MARKET->TRADER").routeId("foo")
                        .filter(header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.EMAIL))
                        .bean(new CountDownLatchDecrementer("message", receivedMessageLatch));
            }
        };

        context.addRoutes(routes);

        LOG.info("Starting Camel context");
        context.start();

        String marketUri = "quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:TRADER->MARKET";
        Producer producer = context.getEndpoint(marketUri).createProducer();

        Email email = TestSupport.createEmailMessage("Example");
        Exchange exchange = producer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        producer.process(exchange);

        // wait a little before stopping
        Thread.sleep(5000);

        // stop route
        context.getRouteController().stopRoute("foo");

        // wait a little before starting
        Thread.sleep(5000);

        // start route again
        context.getRouteController().startRoute("foo");

        // wait a little before sending
        Thread.sleep(5000);

        // send another email
        producer = context.getEndpoint(marketUri).createProducer();
        email = TestSupport.createEmailMessage("Example2");
        exchange = producer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        producer.process(exchange);

        if (!receivedMessageLatch.await(30L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Message did not reach market");
        }

        LOG.info("Message received, shutting down Camel context");

        context.stop();

        LOG.info("Example complete");
    }
}
