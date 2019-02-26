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
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;

import org.apache.activemq.ActiveMQSession;
import org.apache.camel.Endpoint;

/**
 * A JMS {@link javax.jms.QueueSender} which sends message exchanges to a Camel
 * {@link org.apache.camel.Endpoint}
 */
public class CamelQueueSender extends CamelMessageProducer implements QueueSender {

    public CamelQueueSender(CamelQueue destination, Endpoint endpoint, ActiveMQSession session) throws JMSException {
        super(destination, endpoint, session);
    }

    /**
     * Gets the queue associated with this <CODE>QueueSender</CODE>.
     * 
     * @return this sender's queue
     * @throws JMSException if the JMS provider fails to get the queue for this
     *             <CODE>QueueSender</CODE> due to some internal error.
     */

    public Queue getQueue() throws JMSException {
        return (Queue)super.getDestination();
    }

    /**
     * Sends a message to a queue for an unidentified message producer. Uses the
     * <CODE>QueueSender</CODE>'s default delivery mode, priority, and time to
     * live.
     * <p/>
     * <p/>
     * Typically, a message producer is assigned a queue at creation time;
     * however, the JMS API also supports unidentified message producers, which
     * require that the queue be supplied every time a message is sent.
     * 
     * @param queue the queue to send this message to
     * @param message the message to send
     * @throws JMSException if the JMS provider fails to send the message due to
     *             some internal error.
     * @see javax.jms.MessageProducer#getDeliveryMode()
     * @see javax.jms.MessageProducer#getTimeToLive()
     * @see javax.jms.MessageProducer#getPriority()
     */

    public void send(Queue queue, Message message) throws JMSException {
        super.send(queue, message);
    }

    /**
     * Sends a message to a queue for an unidentified message producer,
     * specifying delivery mode, priority and time to live.
     * <p/>
     * <p/>
     * Typically, a message producer is assigned a queue at creation time;
     * however, the JMS API also supports unidentified message producers, which
     * require that the queue be supplied every time a message is sent.
     * 
     * @param queue the queue to send this message to
     * @param message the message to send
     * @param deliveryMode the delivery mode to use
     * @param priority the priority for this message
     * @param timeToLive the message's lifetime (in milliseconds)
     * @throws JMSException if the JMS provider fails to send the message due to
     *             some internal error.
     */

    public void send(Queue queue, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        super.send(queue, message, deliveryMode, priority, timeToLive);
    }
}
