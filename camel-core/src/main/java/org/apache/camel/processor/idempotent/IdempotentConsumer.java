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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/idempotent-consumer.html">Idempotent Consumer</a> pattern.
 * <p/>
 * This implementation supports idempotent repositories implemented as
 * <ul>
 *     <li>IdempotentRepository</li>
 *     <li>ExchangeIdempotentRepository</li>
 * </ul>
 *
 * @see org.apache.camel.spi.IdempotentRepository
 * @see org.apache.camel.spi.ExchangeIdempotentRepository
 */
public class IdempotentConsumer extends ServiceSupport implements AsyncProcessor, Navigate<Processor> {
    private static final Logger LOG = LoggerFactory.getLogger(IdempotentConsumer.class);
    private final Expression messageIdExpression;
    private final AsyncProcessor processor;
    private final IdempotentRepository<String> idempotentRepository;
    private final boolean eager;
    private final boolean skipDuplicate;
    private final boolean removeOnFailure;
    private final AtomicLong duplicateMessageCount = new AtomicLong();

    public IdempotentConsumer(Expression messageIdExpression, IdempotentRepository<String> idempotentRepository,
                              boolean eager, boolean skipDuplicate, boolean removeOnFailure, Processor processor) {
        this.messageIdExpression = messageIdExpression;
        this.idempotentRepository = idempotentRepository;
        this.eager = eager;
        this.skipDuplicate = skipDuplicate;
        this.removeOnFailure = removeOnFailure;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
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
            exchange.setException(new NoMessageIdException(exchange, messageIdExpression));
            callback.done(true);
            return true;
        }

        boolean newKey;
        if (eager) {
            // add the key to the repository
            if (idempotentRepository instanceof ExchangeIdempotentRepository) {
                newKey = ((ExchangeIdempotentRepository<String>) idempotentRepository).add(exchange, messageId);
            } else {
                newKey = idempotentRepository.add(messageId);
            }
        } else {
            // check if we already have the key
            if (idempotentRepository instanceof ExchangeIdempotentRepository) {
                newKey = ((ExchangeIdempotentRepository<String>) idempotentRepository).contains(exchange, messageId);
            } else {
                newKey = !idempotentRepository.contains(messageId);
            }
        }


        if (!newKey) {
            // mark the exchange as duplicate
            exchange.setProperty(Exchange.DUPLICATE_MESSAGE, Boolean.TRUE);

            // we already have this key so its a duplicate message
            onDuplicate(exchange, messageId);

            if (skipDuplicate) {
                // if we should skip duplicate then we are done
                LOG.debug("Ignoring duplicate message with id: {} for exchange: {}", messageId, exchange);
                callback.done(true);
                return true;
            }
        }

        // register our on completion callback
        exchange.addOnCompletion(new IdempotentOnCompletion(idempotentRepository, messageId, eager, removeOnFailure));

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

    public long getDuplicateMessageCount() {
        return duplicateMessageCount.get();
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
     * Resets the duplicate message counter to <code>0L</code>.
     */
    public void resetDuplicateMessageCount() {
        duplicateMessageCount.set(0L);
    }

    private void onDuplicate(Exchange exchange, String messageId) {
        duplicateMessageCount.incrementAndGet();

        onDuplicateMessage(exchange, messageId);
    }

    /**
     * A strategy method to allow derived classes to overload the behaviour of
     * processing a duplicate message
     *
     * @param exchange  the exchange
     * @param messageId the message ID of this exchange
     */
    protected void onDuplicateMessage(Exchange exchange, String messageId) {
        // noop
    }

}
