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
import org.apache.camel.util.ExpressionHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An implementation of the <a
 * href="http://activemq.apache.org/camel/idempotent-consumer.html">Idempotent
 * Consumer</a> pattern.
 * 
 * @version $Revision: 1.1 $
 */
public class IdempotentConsumer extends ServiceSupport implements Processor {
    private static final transient Log LOG = LogFactory.getLog(IdempotentConsumer.class);
    private Expression<Exchange> messageIdExpression;
    private Processor nextProcessor;
    private MessageIdRepository messageIdRepository;

    public IdempotentConsumer(Expression<Exchange> messageIdExpression,
                              MessageIdRepository messageIdRepository, Processor nextProcessor) {
        this.messageIdExpression = messageIdExpression;
        this.messageIdRepository = messageIdRepository;
        this.nextProcessor = nextProcessor;
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[expression=" + messageIdExpression + ", repository=" + messageIdRepository
               + ", processor=" + nextProcessor + "]";
    }

    public void process(Exchange exchange) throws Exception {
        String messageId = ExpressionHelper.evaluateAsString(messageIdExpression, exchange);
        if (messageId == null) {
            throw new NoMessageIdException(exchange, messageIdExpression);
        }
        if (!messageIdRepository.contains(messageId)) {
            nextProcessor.process(exchange);
        } else {
            onDuplicateMessage(exchange, messageId);
        }
    }

    // Properties
    // -------------------------------------------------------------------------
    public Expression<Exchange> getMessageIdExpression() {
        return messageIdExpression;
    }

    public MessageIdRepository getMessageIdRepository() {
        return messageIdRepository;
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
}
