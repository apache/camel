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
package org.apache.camel.component.sjms;

import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * The {@link MessageProducer} resources for all {@link SjmsProducer}
 * classes.
 */
public class MessageProducerResources {

    private final Session session;
    private final MessageProducer messageProducer;
    private final TransactionCommitStrategy commitStrategy;

    public MessageProducerResources(Session session, MessageProducer messageProducer) {
        this(session, messageProducer, null);
    }

    public MessageProducerResources(Session session, MessageProducer messageProducer, TransactionCommitStrategy commitStrategy) {
        this.session = session;
        this.messageProducer = messageProducer;
        this.commitStrategy = commitStrategy;
    }

    /**
     * Gets the Session value of session for this instance of
     * MessageProducerResources.
     *
     * @return the session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Gets the QueueSender value of queueSender for this instance of
     * MessageProducerResources.
     *
     * @return the queueSender
     */
    public MessageProducer getMessageProducer() {
        return messageProducer;
    }

    /**
     * Gets the TransactionCommitStrategy value of commitStrategy for this
     * instance of SjmsProducer.MessageProducerResources.
     *
     * @return the commitStrategy
     */
    public TransactionCommitStrategy getCommitStrategy() {
        return commitStrategy;
    }
}