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

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ConsumerId;

public class MockMessageConsumer extends ActiveMQMessageConsumer {

    private boolean isBadSession;

    public MockMessageConsumer(ActiveMQSession session, ConsumerId consumerId, ActiveMQDestination dest, String name, String selector, int prefetch,
                               int maximumPendingMessageCount, boolean noLocal, boolean browser, boolean dispatchAsync, MessageListener messageListener,
                               boolean isBadSession) throws JMSException {
        super(session, consumerId, dest, name, selector, prefetch, maximumPendingMessageCount, noLocal, browser, dispatchAsync, messageListener);
        this.isBadSession = isBadSession;
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        if (isBadSession) {
            throw new IllegalStateException("asdf");
        }
        return super.receive(timeout);
    }
}
