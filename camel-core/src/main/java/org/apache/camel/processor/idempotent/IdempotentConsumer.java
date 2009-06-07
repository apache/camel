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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
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
public class IdempotentConsumer extends ServiceSupport implements Processor, Navigate<Processor> {
    private static final transient Log LOG = LogFactory.getLog(IdempotentConsumer.class);
    private final Expression messageIdExpression;
    private final Processor processor;
    private final IdempotentRepository idempotentRepository;

    public IdempotentConsumer(Expression messageIdExpression, IdempotentRepository idempotentRepository, Processor processor) {
        this.messageIdExpression = messageIdExpression;
        this.idempotentRepository = idempotentRepository;
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[expression=" + messageIdExpression + ", repository=" + idempotentRepository
               + ", processor=" + processor + "]";
    }

    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        final String messageId = messageIdExpression.evaluate(exchange, String.class);
        if (messageId == null) {
            throw new NoMessageIdException(exchange, messageIdExpression);
        }

        // add the key to the repository
        boolean newKey = idempotentRepository.add(messageId);
        if (!newKey) {
            // we already have this key so its a duplicate message
            onDuplicateMessage(exchange, messageId);
        } else {
            // register our on completion callback
            exchange.addOnCompletion(new IdempotentOnCompletion(idempotentRepository, messageId));

            // process the exchange
            processor.process(exchange);
        }
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(processor);
        return answer;
    }

    public boolean hasNext() {
        return processor != null;
    }

    // Properties
    // -------------------------------------------------------------------------
    public Expression getMessageIdExpression() {
        return messageIdExpression;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    public Processor getProcessor() {
        return processor;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
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
