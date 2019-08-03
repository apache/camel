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
package org.apache.camel.component.sjms.support;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQMessageTransformation;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.CustomDestination;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.activemq.command.SessionId;

public class MockSession extends ActiveMQSession {
    private boolean isBadSession;

    protected MockSession(ActiveMQConnection connection, SessionId sessionId, int acknowledgeMode, boolean asyncDispatch, boolean sessionAsyncDispatch, boolean isBadSession) throws JMSException {
        super(connection,  sessionId,  acknowledgeMode,  asyncDispatch,  sessionAsyncDispatch);
        this.isBadSession = isBadSession;
    }
    @Override
    public Queue createQueue(String queueName) throws JMSException {
        this.checkClosed();
        return queueName.startsWith("ID:") ? new ActiveMQTempQueue(queueName) : new ActiveMQQueue(queueName);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal, MessageListener messageListener) throws JMSException {
        this.checkClosed();
        if (destination instanceof CustomDestination) {
            CustomDestination prefetchPolicy1 = (CustomDestination)destination;
            return prefetchPolicy1.createConsumer(this, messageSelector, noLocal);
        } else {
            ActiveMQPrefetchPolicy prefetchPolicy = this.connection.getPrefetchPolicy();
            int prefetch1;
            if (destination instanceof Topic) {
                prefetch1 = prefetchPolicy.getTopicPrefetch();
            } else {
                prefetch1 = prefetchPolicy.getQueuePrefetch();
            }

            ActiveMQDestination activemqDestination = ActiveMQMessageTransformation.transformDestination(destination);
            return new MockMessageConsumer(this, this.getNextConsumerId(), activemqDestination, (String)null, messageSelector, prefetch1, prefetchPolicy.getMaximumPendingMessageLimit(),
                                           noLocal, false, this.isAsyncDispatch(), messageListener, isBadSession);
        }
    }
}
