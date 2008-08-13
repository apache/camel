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
package org.apache.camel.processor;


import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.RoutingSlipType;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a <a href="http://activemq.apache.org/camel/routing-slip.html">Routing Slip</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on the value of a message header.
 */
public class RoutingSlip extends ServiceSupport implements Processor {
    private static final transient Log LOG = LogFactory.getLog(RoutingSlip.class);
    private final String header;
    private final String uriDelimiter;

    private ProducerCache<Exchange> producerCache = new ProducerCache<Exchange>();

    public RoutingSlip(String header) {
        this(header, RoutingSlipType.DEFAULT_DELIMITER);
    }

    public RoutingSlip(String header, String uriDelimiter) {
        notNull(header, "header");
        notNull(uriDelimiter, "uriDelimiter");

        this.header = header;
        this.uriDelimiter = uriDelimiter;
    }

    @Override
    public String toString() {
        return "RoutingSlip[header=" + header + " uriDelimiter=" + uriDelimiter + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        String[] recipients = recipients(message);
        Exchange current = exchange;

        for (String nextRecipient : recipients) {
            Endpoint<Exchange> endpoint = resolveEndpoint(exchange, nextRecipient);
            Producer<Exchange> producer = producerCache.getProducer(endpoint);
            Exchange ex = current.newInstance();

            updateRoutingSlip(current);
            copyOutToIn(ex, current);

            producer.process(ex);

            current = ex;
        }
        ExchangeHelper.copyResults(exchange, current);
    }

    protected Endpoint<Exchange> resolveEndpoint(Exchange exchange, Object recipient) {
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected void doStop() throws Exception {
        producerCache.stop();
    }

    protected void doStart() throws Exception {
    }

    private void updateRoutingSlip(Exchange current) {
        Message message = getResultMessage(current);
        message.setHeader(header, removeFirstElement(recipients(message)));
    }

    /**
     * Returns the outbound message if available. Otherwise return the inbound
     * message.
     */
    private Message getResultMessage(Exchange exchange) {
        Message message = exchange.getOut(false);
        // if this endpoint had no out (like a mock endpoint)
        // just take the in
        if (message == null) {
            message = exchange.getIn();
        }
        return message;
    }

    /**
     * Return the list of recipients defined in the routing slip in the
     * specified message.
     */
    private String[] recipients(Message message) {
        Object headerValue = message.getHeader(header);
        if (headerValue != null && !headerValue.equals("")) {
            return headerValue.toString().split(uriDelimiter);
        }
        return new String[] {};
    }

    /**
     * Return a string representation of the element list with the first element
     * removed.
     */
    private String removeFirstElement(String[] elements) {
        CollectionStringBuffer updatedElements = new CollectionStringBuffer(uriDelimiter);
        for (int i = 1; i < elements.length; i++) {
            updatedElements.append(elements[i]);
        }
        return updatedElements.toString();
    }

    /**
     * Copy the outbound data in 'source' to the inbound data in 'result'.
     */
    private void copyOutToIn(Exchange result, Exchange source) {
        result.setException(source.getException());

        Message fault = source.getFault(false);
        if (fault != null) {
            result.getFault(true).copyFrom(fault);
        }

        result.setIn(getResultMessage(source));

        result.getProperties().clear();
        result.getProperties().putAll(source.getProperties());
    }
}
