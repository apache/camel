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
package org.apache.camel.component.quickfixj;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.impl.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.Message;
import quickfix.SessionID;

public class QuickfixjEndpoint extends DefaultEndpoint implements QuickfixjEventListener, MultipleConsumersSupport {
    public static final String EVENT_CATEGORY_KEY = "EventCategory";
    public static final String SESSION_ID_KEY = "SessionID";
    public static final String MESSAGE_TYPE_KEY = "MessageType";
    public static final String DATA_DICTIONARY_KEY = "DataDictionary";
    
    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjEndpoint.class);
    
    private SessionID sessionID;
    private List<QuickfixjConsumer> consumers = new CopyOnWriteArrayList<QuickfixjConsumer>();
    
    public QuickfixjEndpoint(String uri, CamelContext context) {
        super(uri, context);
    }

    protected SessionID getSessionID() {
        return sessionID;
    }

    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.info("Creating QuickFIX/J consumer: " + (sessionID != null ? sessionID : "No Session"));
        QuickfixjConsumer consumer = new QuickfixjConsumer(this, processor);
        // TODO The lifecycle mgmt requirements aren't clear to me
        consumer.start();
        consumers.add(consumer);
        return consumer;
    }

    public Producer createProducer() throws Exception {
        LOG.info("Creating QuickFIX/J producer: " + (sessionID != null ? sessionID : "No Session"));
        return new QuickfixjProducer(this);
    }

    public boolean isSingleton() {
        // TODO This seems to be incorrect. There can be multiple consumers for a session endpoint.
        return true;
    }

    public void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) throws Exception {
        if (this.sessionID == null || this.sessionID.equals(sessionID)) {
            for (QuickfixjConsumer consumer : consumers) {
                Exchange exchange = QuickfixjConverters.toExchange(this, sessionID, message, eventCategory);
                consumer.onExchange(exchange);
                if (exchange.getException() != null) {
                    throw exchange.getException();
                }
            }
        }
    }
    
    public boolean isMultipleConsumersSupported() {
        return true;
    }
}
