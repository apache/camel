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
package org.apache.camel.component.quickfixj;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.quickfixj.QFJException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Session;
import quickfix.SessionID;

public class QuickfixjConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjConsumer.class);

    public QuickfixjConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public QuickfixjEndpoint getEndpoint() {
        return (QuickfixjEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        getEndpoint().addConsumer(this);
        getEndpoint().ensureInitialized();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().removeConsumer(this);
        super.doStop();
    }

    public void onExchange(Exchange exchange) {
        if (isStarted()) {
            try {
                // the reply goes back on the session the request arrived on, so capture it before routing
                // can change the header
                SessionID messageSessionID = exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY, SessionID.class);
                // only an application message received is a request; the other events (such as the Logon being sent)
                // must never be answered
                QuickfixjEventCategory category
                        = exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY, QuickfixjEventCategory.class);
                boolean replyExpected
                        = exchange.getPattern().isOutCapable() && category == QuickfixjEventCategory.AppMessageReceived;
                Object request = exchange.getIn().getBody();

                getProcessor().process(exchange);

                if (replyExpected && !exchange.isFailed()) {
                    sendOutMessage(exchange, messageSessionID, request);
                }
            } catch (Exception e) {
                exchange.setException(e);
            }
        }
    }

    private void sendOutMessage(Exchange exchange, SessionID messageSessionID, Object request) throws QFJException {
        Message camelMessage = exchange.getMessage();
        Object body = camelMessage.getBody();
        if (body == null || body == request) {
            // the route set no reply (an InOut route ends with the message it received when it does not set one)
            LOG.debug("No FIX message reply to send for exchange: {}", exchange.getExchangeId());
            return;
        }

        quickfix.Message quickfixjMessage;
        try {
            quickfixjMessage = camelMessage.getMandatoryBody(quickfix.Message.class);
        } catch (InvalidPayloadException e) {
            // the request has been routed, so it must not fail inside the QuickFIX/J callback, where the engine would
            // ask the counterparty to resend it and route it again, over and over
            getExceptionHandler().handleException("Cannot send the reply, as it is not a FIX message", exchange, e);
            return;
        }

        LOG.debug("Sending FIX message reply: {}", quickfixjMessage);

        Session session = getSession(messageSessionID);
        if (session == null) {
            throw new IllegalStateException("Unknown session: " + messageSessionID);
        }

        if (!session.send(quickfixjMessage)) {
            throw new CannotSendException("Could not send FIX message reply: " + quickfixjMessage.toString());
        }
    }

    Session getSession(SessionID messageSessionID) {
        return Session.lookupSession(messageSessionID);
    }
}
