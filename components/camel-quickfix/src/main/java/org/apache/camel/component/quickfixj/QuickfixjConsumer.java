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
    protected void doStart() throws Exception {
        ((QuickfixjEndpoint)getEndpoint()).ensureInitialized();
        super.doStart();
    }

    public void onExchange(Exchange exchange) throws Exception {
        if (isStarted()) {
            try {
                getProcessor().process(exchange);
                if (exchange.getPattern().isOutCapable() && exchange.hasOut()) {
                    sendOutMessage(exchange);
                }
            } catch (Exception e) {
                exchange.setException(e);
            }
        }
    }

    private void sendOutMessage(Exchange exchange) throws QFJException {
        Message camelMessage = exchange.getMessage();
        quickfix.Message quickfixjMessage = camelMessage.getBody(quickfix.Message.class);

        LOG.debug("Sending FIX message reply: {}", quickfixjMessage);

        SessionID messageSessionID = exchange.getIn().getHeader("SessionID", SessionID.class);

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
