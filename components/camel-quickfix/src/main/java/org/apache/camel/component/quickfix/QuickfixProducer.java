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
package org.apache.camel.component.quickfix;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.log4j.Logger;
import quickfix.Message;

/**
 * QuickfixProducer is intended to be used as an initiator instance.
 * <p/>
 * The initiator will send the FIX messages to the configured acceptors via session object.
 *
 * @author Anton Arhipov
 */
public class QuickfixProducer extends DefaultProducer {

    private static final Logger LOG = Logger.getLogger(QuickfixProducer.class);

    private QuickfixEndpoint endpoint;

    public QuickfixProducer(QuickfixEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * send the exchange further to acceptor side
     * 
     * @param exchange the normalized message
     * @throws Exception
     */
    public void process(Exchange exchange) throws Exception {
        Message message = toQuickMessage(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending FIX message : " + message);
        }
        endpoint.getSession().send(message);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sent FIX message : " + message);
        }
    }

    /**
     * invokes the converter logic
     * 
     * @param exchange the exchange
     * @return quickfixj's message
     * @throws IOException
     * @throws InvalidPayloadException
     */
    protected Message toQuickMessage(Exchange exchange) throws InvalidPayloadException, IOException {
        return ExchangeHelper.getMandatoryInBody(exchange, Message.class);
    }

}
