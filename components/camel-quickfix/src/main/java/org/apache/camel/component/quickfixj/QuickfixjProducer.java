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

import java.util.concurrent.Callable;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;

public class QuickfixjProducer extends DefaultProducer {
    public static final String CORRELATION_TIMEOUT_KEY = "CorrelationTimeout";
    public static final String CORRELATION_CRITERIA_KEY = "CorrelationCriteria";
    
    private final SessionID sessionID;
    
    public QuickfixjProducer(Endpoint endpoint) {
        super(endpoint);
        sessionID = ((QuickfixjEndpoint) getEndpoint()).getSessionID();
    }

    public void process(Exchange exchange) throws Exception {
        sendMessage(exchange, exchange.getIn());
    }

    void sendMessage(Exchange exchange, org.apache.camel.Message camelMessage) throws InterruptedException {
        try {
            Message message = camelMessage.getBody(Message.class);
            log.debug("Sending FIX message: {}", message);

            SessionID messageSessionID = sessionID;
            if (messageSessionID == null) {
                messageSessionID = MessageUtils.getSessionID(message);
            }

            Session session = getSession(messageSessionID);
            if (session == null) {
                throw new IllegalStateException("Unknown session: " + messageSessionID);
            }

            Callable<Message> callable = null;

            if (exchange.getPattern().isOutCapable()) {
                QuickfixjEndpoint endpoint = (QuickfixjEndpoint) getEndpoint();
                MessageCorrelator messageCorrelator = endpoint.getEngine().getMessageCorrelator();
                callable = messageCorrelator.getReply(endpoint.getSessionID(), exchange);
            }

            session.send(message);

            if (callable != null) {
                Message reply = callable.call();
                exchange.getOut().setBody(reply);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    Session getSession(SessionID messageSessionID) {
        return Session.lookupSession(messageSessionID);
    }
}
