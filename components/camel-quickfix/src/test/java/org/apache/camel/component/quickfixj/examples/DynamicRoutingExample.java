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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.component.quickfixj.TestSupport;
import org.apache.camel.component.quickfixj.examples.routing.FixMessageRouter;
import org.apache.camel.component.quickfixj.examples.transform.QuickfixjMessageJsonPrinter;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.field.DeliverToCompID;
import quickfix.field.MsgType;
import quickfix.fix42.Email;

public class DynamicRoutingExample {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicRoutingExample.class);

    public static void main(String[] args) throws Exception {
        new DynamicRoutingExample().sendMessage();
    }
    
    public void sendMessage() throws Exception {        
        DefaultCamelContext context = new DefaultCamelContext();
        
        final CountDownLatch logonLatch = new CountDownLatch(4);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        
        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Release latch when session logon events are received
                // We expect four logon events (four sessions)
                from("quickfix:examples/gateway.cfg").
                    filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon)).
                    bean(new CountDownLatchDecrementer("logon", logonLatch));

                // Dynamic router -- Uses FIX DeliverTo tags
                from("quickfix:examples/gateway.cfg").
                    filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived)).
                    recipientList(method(new FixMessageRouter("quickfix:examples/gateway.cfg")));

                // Logger app messages as JSON
                from("quickfix:examples/gateway.cfg").
                    filter(PredicateBuilder.or(
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived),
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageSent))).
                    bean(new QuickfixjMessageJsonPrinter());
                
                // If the trader@2 session receives an email then release the latch
                from("quickfix:examples/gateway.cfg?sessionID=FIX.4.2:TRADER@2->GATEWAY").
                    filter(PredicateBuilder.and(
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived),
                            header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.EMAIL))).
                    bean(new CountDownLatchDecrementer("message", receivedMessageLatch));
            }
        };
        
        context.addRoutes(routes);
        
        LOG.info("Starting Camel context");
        context.start();
        
        // This is not strictly necessary, but it prevents the need for session
        // synchronization due to app messages being sent before being logged on
        if (!logonLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Logon did not complete");
        }
        
        String gatewayUri = "quickfix:examples/gateway.cfg?sessionID=FIX.4.2:TRADER@1->GATEWAY";
        Endpoint gatewayEndpoint = context.getEndpoint(gatewayUri);
        Producer producer = gatewayEndpoint.createProducer();
        
        Email email = TestSupport.createEmailMessage("Dynamic Routing Example");
        email.getHeader().setString(DeliverToCompID.FIELD, "TRADER@2");
        
        LOG.info("Sending routed message");
        
        Exchange exchange = producer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        producer.process(exchange);            

        if (!receivedMessageLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Message did not reach target");
        }
        
        LOG.info("Message received, shutting down Camel context");
        
        context.stop();
        
        LOG.info("Dynamic routing example complete");
    }
}
