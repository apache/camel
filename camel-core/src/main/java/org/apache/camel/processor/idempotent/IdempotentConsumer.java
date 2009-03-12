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
package org.apache.camel.processor.idempotent;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/idempotent-consumer.html">Idempotent
 * Consumer</a> pattern.
 * 
 * @version $Revision$
 */
public class IdempotentConsumer extends ServiceSupport implements Processor {
    private static final transient Log LOG = LogFactory.getLog(IdempotentConsumer.class);
    private final Expression messageIdExpression;
    private final Processor nextProcessor;
    private final IdempotentRepository idempotentRepository;

    public IdempotentConsumer(Expression messageIdExpression, IdempotentRepository idempotentRepository,
                              Processor nextProcessor) {
        this.messageIdExpression = messageIdExpression;
        this.idempotentRepository = idempotentRepository;
        this.nextProcessor = nextProcessor;
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[expression=" + messageIdExpression + ", repository=" + idempotentRepository
               + ", processor=" + nextProcessor + "]";
    }

    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        String messageId = messageIdExpression.evaluate(exchange, String.class);
        if (messageId == null) {
            throw new NoMessageIdException(exchange, messageIdExpression);
        }

        if (idempotentRepository.contains(messageId)) {
            onDuplicateMessage(exchange, messageId);
        } else {
            // process it first
            nextProcessor.process(exchange);

            // then test wheter it was failed or not
            if (!exchange.isFailed()) {
                onCompletedMessage(exchange, messageId);
            } else {
                onFailedMessage(exchange, messageId);
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------
    public Expression getMessageIdExpression() {
        return messageIdExpression;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    public Processor getNextProcessor() {
        return nextProcessor;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected void doStart() throws Exception {
        ServiceHelper.startServices(nextProcessor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(nextProcessor);
    }

    /**
     * A strategy method to allow derived classes to overload the behaviour of
     * processing a duplicate message
     * 
     * @param exchange the exchange
     * @param messageId the message ID of this exchange
     */
    protected void onDuplicateMessage(Exchange exchange, String messageId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ignoring duplicate message with id: " + messageId + " for exchange: " + exchange);
        }
    }

    /**
     * A strategy method to allow derived classes to overload the behaviour of
     * processing a completed message
     *
     * @param exchange the exchange
     * @param messageId the message ID of this exchange
     */
    @SuppressWarnings("unchecked")
    protected void onCompletedMessage(Exchange exchange, String messageId) {
        idempotentRepository.add(messageId);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Added to repository with id: " + messageId + " for exchange: " + exchange);
        }
    }

    /**
     * A strategy method to allow derived classes to overload the behaviour of
     * processing a failed message
     *
     * @param exchange the exchange
     * @param messageId the message ID of this exchange
     */
    protected void onFailedMessage(Exchange exchange, String messageId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Not added to repository as exchange failed: " + exchange + " with id: " + messageId);
        }
    }
}
