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
package org.apache.camel.component.quickfixj.examples.trading;

import java.time.LocalDateTime;
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
import org.apache.camel.component.quickfixj.examples.transform.QuickfixjMessageJsonPrinter;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.field.ClOrdID;
import quickfix.field.HandlInst;
import quickfix.field.MsgType;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix42.NewOrderSingle;

public class TradeExecutorExample {
    private static final Logger LOG = LoggerFactory.getLogger(TradeExecutorExample.class);

    public static void main(String[] args) throws Exception {
        new TradeExecutorExample().sendMessage();
    }
    
    public void sendMessage() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("trade-executor", new TradeExecutorComponent());
        
        final CountDownLatch logonLatch = new CountDownLatch(2);
        final CountDownLatch executionReportLatch = new CountDownLatch(2);
        
        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Release latch when session logon events are received
                from("quickfix:examples/inprocess.cfg").
                    filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon)).
                    bean(new CountDownLatchDecrementer("logon", logonLatch));

                from("quickfix:examples/inprocess.cfg?sessionID=FIX.4.2:MARKET->TRADER").
                    filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived)).
                    to("trade-executor:market");

                from("trade-executor:market").to("quickfix:examples/inprocess.cfg");
                
                // Logger app messages as JSON
                from("quickfix:examples/inprocess.cfg").
                    filter(PredicateBuilder.or(
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived),
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageSent))).
                    bean(new QuickfixjMessageJsonPrinter());
                
                // Release latch when trader receives execution report
                from("quickfix:examples/inprocess.cfg?sessionID=FIX.4.2:TRADER->MARKET").
                    filter(PredicateBuilder.and(
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived),
                        header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.EXECUTION_REPORT))).
                    bean(new CountDownLatchDecrementer("execution report", executionReportLatch));
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
        
        String gatewayUri = "quickfix:examples/inprocess.cfg?sessionID=FIX.4.2:TRADER->MARKET";
        Endpoint gatewayEndpoint = context.getEndpoint(gatewayUri);
        Producer producer = gatewayEndpoint.createProducer();
        
        LOG.info("Sending order");
        
        NewOrderSingle order = createNewOrderMessage();
        Exchange exchange = producer.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(order);
        producer.process(exchange);            

        if (!executionReportLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Did not receive execution reports");
        }
        
        LOG.info("Message received, shutting down Camel context");
        
        context.stop();
        
        LOG.info("Order execution example complete");
    }

    private NewOrderSingle createNewOrderMessage() {
        NewOrderSingle order = new NewOrderSingle(
            new ClOrdID("CLIENT_ORDER_ID"), 
            new HandlInst('1'), 
            new Symbol("GOOG"), 
            new Side(Side.BUY), 
            new TransactTime(LocalDateTime.now()), 
            new OrdType(OrdType.LIMIT));
        
        order.set(new OrderQty(10));
        order.set(new Price(300.00));
        
        return order;
    }
}
