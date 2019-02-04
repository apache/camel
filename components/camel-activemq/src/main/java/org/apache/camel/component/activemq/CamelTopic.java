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
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;

import org.apache.activemq.ActiveMQSession;

/**
 * A JMS {@link javax.jms.Topic} object which refers to a Camel endpoint
 */
public class CamelTopic extends CamelDestination implements Topic {

    public CamelTopic(String uri) {
        super(uri);
    }

    public String getTopicName() throws JMSException {
        return getUri();
    }

    public TopicPublisher createPublisher(ActiveMQSession session) throws JMSException {
        return new CamelTopicPublisher(this, resolveEndpoint(session), session);
    }

    public TopicSubscriber createDurableSubscriber(ActiveMQSession session, String name, String messageSelector, boolean noLocal) {
        return new CamelTopicSubscriber(this, resolveEndpoint(session), session, name, messageSelector, noLocal);
    }

}
