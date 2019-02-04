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
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.apache.activemq.ActiveMQSession;
import org.apache.camel.Endpoint;

/**
 * A JMS {@link javax.jms.TopicPublisher} which sends message exchanges to a
 * Camel {@link Endpoint}
 */
public class CamelTopicPublisher extends CamelMessageProducer implements TopicPublisher {

    public CamelTopicPublisher(CamelTopic destination, Endpoint endpoint, ActiveMQSession session) throws JMSException {
        super(destination, endpoint, session);
    }

    /**
     * Gets the topic associated with this <CODE>TopicPublisher</CODE>.
     *
     * @return this publisher's topic
     * @throws JMSException if the JMS provider fails to get the topic for this
     *             <CODE>TopicPublisher</CODE> due to some internal error.
     */

    public Topic getTopic() throws JMSException {
        return (Topic)super.getDestination();
    }

    /**
     * Publishes a message to the topic. Uses the <CODE>TopicPublisher</CODE>'s
     * default delivery mode, priority, and time to live.
     *
     * @param message the message to publish
     * @throws JMSException if the JMS provider fails to publish the message due
     *             to some internal error.
     * @throws javax.jms.MessageFormatException if an invalid message is
     *             specified.
     * @throws javax.jms.InvalidDestinationException if a client uses this
     *             method with a <CODE>TopicPublisher
     *                                     </CODE> with an invalid topic.
     * @throws java.lang.UnsupportedOperationException if a client uses this
     *             method with a <CODE>TopicPublisher
     *                                     </CODE> that did not specify a topic
     *             at creation time.
     * @see javax.jms.MessageProducer#getDeliveryMode()
     * @see javax.jms.MessageProducer#getTimeToLive()
     * @see javax.jms.MessageProducer#getPriority()
     */

    public void publish(Message message) throws JMSException {
        super.send(message);
    }

    /**
     * Publishes a message to the topic, specifying delivery mode, priority, and
     * time to live.
     *
     * @param message the message to publish
     * @param deliveryMode the delivery mode to use
     * @param priority the priority for this message
     * @param timeToLive the message's lifetime (in milliseconds)
     * @throws JMSException if the JMS provider fails to publish the message due
     *             to some internal error.
     * @throws javax.jms.MessageFormatException if an invalid message is
     *             specified.
     * @throws javax.jms.InvalidDestinationException if a client uses this
     *             method with a <CODE>TopicPublisher
     *                                     </CODE> with an invalid topic.
     * @throws java.lang.UnsupportedOperationException if a client uses this
     *             method with a <CODE>TopicPublisher
     *                                     </CODE> that did not specify a topic
     *             at creation time.
     */

    public void publish(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        super.send(message, deliveryMode, priority, timeToLive);
    }

    /**
     * Publishes a message to a topic for an unidentified message producer. Uses
     * the <CODE>TopicPublisher</CODE>'s default delivery mode, priority, and
     * time to live.
     * <p/>
     * <P>
     * Typically, a message producer is assigned a topic at creation time;
     * however, the JMS API also supports unidentified message producers, which
     * require that the topic be supplied every time a message is published.
     *
     * @param topic the topic to publish this message to
     * @param message the message to publish
     * @throws JMSException if the JMS provider fails to publish the message due
     *             to some internal error.
     * @throws javax.jms.MessageFormatException if an invalid message is
     *             specified.
     * @throws javax.jms.InvalidDestinationException if a client uses this
     *             method with an invalid topic.
     * @see javax.jms.MessageProducer#getDeliveryMode()
     * @see javax.jms.MessageProducer#getTimeToLive()
     * @see javax.jms.MessageProducer#getPriority()
     */

    public void publish(Topic topic, Message message) throws JMSException {
        super.send(topic, message);
    }

    /**
     * Publishes a message to a topic for an unidentified message producer,
     * specifying delivery mode, priority and time to live.
     * <p/>
     * <P>
     * Typically, a message producer is assigned a topic at creation time;
     * however, the JMS API also supports unidentified message producers, which
     * require that the topic be supplied every time a message is published.
     *
     * @param topic the topic to publish this message to
     * @param message the message to publish
     * @param deliveryMode the delivery mode to use
     * @param priority the priority for this message
     * @param timeToLive the message's lifetime (in milliseconds)
     * @throws JMSException if the JMS provider fails to publish the message due
     *             to some internal error.
     * @throws javax.jms.MessageFormatException if an invalid message is
     *             specified.
     * @throws javax.jms.InvalidDestinationException if a client uses this
     *             method with an invalid topic.
     */

    public void publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        super.send(topic, message, deliveryMode, priority, timeToLive);
    }
}
