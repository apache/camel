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
package org.apache.camel.component.quickfixj.examples.trading;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;
import quickfix.Message.Header;
import quickfix.SessionID;
import quickfix.field.SenderCompID;
import quickfix.field.SenderLocationID;
import quickfix.field.SenderSubID;
import quickfix.field.TargetCompID;
import quickfix.field.TargetLocationID;
import quickfix.field.TargetSubID;

/**
 * Adapts the TradeExecutor for use as a Camel endpoint.
 * 
 * @see TradeExecutor
 */
public class TradeExecutorComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(TradeExecutorComponent.class);

    private Map<String, TradeExecutorEndpoint> endpoints = new HashMap<>();
    private final Executor executor;

    public TradeExecutorComponent() {
        this(Executors.newCachedThreadPool(new TradeExecutorThreadFactory()));
    }
    
    private static class TradeExecutorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Trade Executor");
            thread.setDaemon(true);
            return thread;
        }
    }
    
    public TradeExecutorComponent(Executor executor) {
        this.executor = executor;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        synchronized (endpoints) {
            Endpoint endpoint = endpoints.get(uri);
            if (endpoint == null) {
                endpoint = new TradeExecutorEndpoint(uri, new TradeExecutor());
                endpoints.put(uri, (TradeExecutorEndpoint) endpoint);
                LOG.info("Created trade executor: " + uri);
            }
            return endpoint;
        }
    }

    private class TradeExecutorEndpoint extends DefaultEndpoint {
        private final TradeExecutor tradeExecutor;
        private List<Processor> processors = new CopyOnWriteArrayList<>();
        
        TradeExecutorEndpoint(String uri, TradeExecutor tradeExecutor) {
            super(uri, TradeExecutorComponent.this);
            this.tradeExecutor = tradeExecutor;
            tradeExecutor.addListener(new QuickfixjMessageListener() {
                @Override
                public void onMessage(SessionID sessionID, Message message) throws Exception {
                    // Inject session ID into message so producer will know where to send it
                    Header header = message.getHeader();
                    setOptionalField(header, sessionID, SenderCompID.FIELD, sessionID.getTargetCompID());
                    setOptionalField(header, sessionID, SenderSubID.FIELD, sessionID.getTargetSubID());
                    setOptionalField(header, sessionID, SenderLocationID.FIELD, sessionID.getTargetLocationID());
                    setOptionalField(header, sessionID, TargetCompID.FIELD, sessionID.getSenderCompID());
                    setOptionalField(header, sessionID, TargetSubID.FIELD, sessionID.getSenderSubID());
                    setOptionalField(header, sessionID, TargetLocationID.FIELD, sessionID.getSenderLocationID());
                    
                    Exchange exchange = QuickfixjConverters.toExchange(
                        TradeExecutorEndpoint.this, sessionID, message, 
                        QuickfixjEventCategory.AppMessageReceived);
                    
                    for (Processor processor : processors) {
                        processor.process(exchange);
                    }
                }

                private void setOptionalField(Header header, SessionID sessionID, int tag, String value) {
                    if (!ObjectHelper.isEmpty(value)) {
                        header.setString(tag, value);
                    }
                }
            });
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {
                @Override
                public void process(final Exchange exchange) throws Exception {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                tradeExecutor.execute(exchange.getIn().getMandatoryBody(Message.class));
                            } catch (Exception e) {
                                LOG.error("Error during trade execution", e);
                            }
                        }
                    });
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    processors.add(getProcessor());
                }
                
                @Override
                protected void doStop() throws Exception {
                    processors.remove(getProcessor());                   
                }
            };
        }

        @Override
        public boolean isSingleton() {
            return false;
        }        
    }
}
