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
package org.apache.camel.component.sjms.reply;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.camel.component.sjms.MessageListenerContainer;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;

/**
 * This {@link MessageListenerContainer} is used for reply queues which are using temporary queue.
 */
public class TemporaryQueueMessageListenerContainer extends SimpleMessageListenerContainer {

    // no need to override any methods currently

    public TemporaryQueueMessageListenerContainer(SjmsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected Session createSession(Connection connection, SjmsEndpoint endpoint) throws Exception {
        // cannot be transacted when doing request/reply
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    protected MessageConsumer createMessageConsumer(Session session) throws Exception {
        Destination destination = getDestinationCreationStrategy().createTemporaryDestination(session, false);
        return getEndpoint().getJmsObjectFactory().createQueueMessageConsumer(session, destination);
    }
}
