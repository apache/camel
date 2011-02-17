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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/idempotent-consumer.html">Idempotent Consumer</a> pattern.
 * 
 * @version 
 */
public class IdempotentConsumer extends ServiceSupport implements AsyncProcessor, Navigate<Processor> {
    private static final transient Logger LOG = LoggerFactory.getLogger(IdempotentConsumer.class);
    private final Expression messageIdExpression;
    private final AsyncProcessor processor;
    private final IdempotentRepository<String> idempotentRepository;
    private final boolean eager;

    public IdempotentConsumer(Expression messageIdExpression, IdempotentRepository<String> idempotentRepository,
                              boolean eager, Processor processor) {
        this.messageIdExpression = messageIdExpression;
        this.idempotentRepository = idempotentRepository;
        this.eager = eager;
        this.processor = AsyncProcessorTypeConverter.convert(processor);
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[" + messageIdExpression + " -> " + processor + "]";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        final String messageId = messageIdExpression.evaluate(exchange, String.class);
        if (messageId == null) {
            throw new NoMessageIdException(exchange, messageIdExpression);
        }

        boolean newKey;
        if (eager) {
            // add the key to the repository
            newKey = idempotentRepository.add(messageId);
        } else {
            // check if we already have the key
            newKey = !idempotentRepository.contains(messageId);
        }

        if (!newKey) {
            // we already have this key so its a duplicate message
            onDuplicateMessage(exchange, messageId);
            callback.done(true);
            return true;
        }

        // register our on completion callback
        exchange.addOnCompletion(new IdempotentOnCompletion(idempotentRepository, messageId, eager));

        // process the exchange
        return processor.process(exchange, callback);
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

    public IdempotentRepository<String> getIdempotentRepository() {
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
