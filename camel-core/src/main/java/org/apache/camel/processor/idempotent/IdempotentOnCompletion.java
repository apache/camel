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
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Synchronization;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * On completion strategy for {@link org.apache.camel.processor.idempotent.IdempotentConsumer}.
 * <p/>
 * This strategy adds the message id to the idempotent repository in cast the exchange
 * was processed successfully. In case of failure the message id is <b>not</b> added.
 *
 * @version $Revision$
 */
public class IdempotentOnCompletion implements Synchronization {

    private static final transient Log LOG = LogFactory.getLog(IdempotentOnCompletion.class);
    private final IdempotentRepository idempotentRepository;
    private final String messageId;

    public IdempotentOnCompletion(IdempotentRepository idempotentRepository, String messageId) {
        this.idempotentRepository = idempotentRepository;
        this.messageId = messageId;
    }

    @SuppressWarnings("unchecked")
    public void onComplete(Exchange exchange) {
        onCompletedMessage(exchange, messageId);
    }

    public void onFailure(Exchange exchange) {
        onFailedMessage(exchange, messageId);
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

    @Override
    public String toString() {
        return "IdempotentOnCompletion[" + messageId + ']';
    }
}
