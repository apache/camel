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
package org.apache.camel.component.activemq;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.activemq.ActiveMQSession;
import org.apache.camel.Endpoint;

/**
 * A JMS {@link javax.jms.TopicSubscriber} which consumes message exchanges from
 * a Camel {@link Endpoint}
 */
public class CamelTopicSubscriber extends CamelMessageConsumer implements TopicSubscriber {

    public CamelTopicSubscriber(CamelTopic destination, Endpoint endpoint, ActiveMQSession session, String name, String messageSelector, boolean noLocal) {
        super(destination, endpoint, session, messageSelector, noLocal);
    }

    /**
     * Gets the <CODE>Topic</CODE> associated with this subscriber.
     *
     * @return this subscriber's <CODE>Topic</CODE>
     * @throws javax.jms.JMSException if the JMS provider fails to get the topic
     *             for this topic subscriber due to some internal error.
     */

    public Topic getTopic() throws JMSException {
        checkClosed();
        return (Topic)super.getDestination();
    }

    /**
     * Gets the <CODE>NoLocal</CODE> attribute for this subscriber. The default
     * value for this attribute is false.
     *
     * @return true if locally published messages are being inhibited
     * @throws JMSException if the JMS provider fails to get the <CODE>NoLocal
     *                      </CODE> attribute for this topic subscriber due to
     *             some internal error.
     */

    public boolean getNoLocal() throws JMSException {
        checkClosed();
        return super.isNoLocal();
    }
}
