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
package org.apache.camel.component.sjms.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.camel.util.URISupport;

/**
 * Default implementation of DestinationCreationStrategy, delegates to Session.createTopic
 * and Session.createQueue.
 *
 * @see org.apache.camel.component.sjms.jms.DestinationCreationStrategy
 * @see javax.jms.Session
 */
public class DefaultDestinationCreationStrategy implements DestinationCreationStrategy {

    @Override
    public Destination createDestination(final Session session, String name, final boolean topic) throws JMSException {
        Destination destination;

        if (topic) {
            name = URISupport.stripPrefix(name, "topic://");
            name = URISupport.stripPrefix(name, "topic:");
            destination = session.createTopic(name);
        } else {
            name = URISupport.stripPrefix(name, "queue://");
            name = URISupport.stripPrefix(name, "queue:");
            destination = session.createQueue(name);
        }

        return destination;
    }

    @Override
    public Destination createTemporaryDestination(final Session session, final boolean topic) throws JMSException {
        if (topic) {
            return session.createTemporaryTopic();
        } else {
            return session.createTemporaryQueue();
        }
    }
}
