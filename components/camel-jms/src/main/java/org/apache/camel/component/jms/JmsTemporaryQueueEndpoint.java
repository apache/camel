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
package org.apache.camel.component.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

/**
 * A <a href="http://activemq.apache.org/jms.html">JMS Endpoint</a>
 * for working with a {@link TemporaryQueue}
 * <p/>
 * <b>Important:</b> Need to be really careful to always use the same Connection otherwise the destination goes stale
 *
 * @version 
 */
public class JmsTemporaryQueueEndpoint extends JmsQueueEndpoint implements DestinationEndpoint {
    private Destination jmsDestination;

    public JmsTemporaryQueueEndpoint(String uri, JmsComponent component, String destination, JmsConfiguration configuration) {
        super(uri, component, destination, configuration);
        setDestinationType("temp-queue");
    }

    public JmsTemporaryQueueEndpoint(String uri, JmsComponent component, String destination, JmsConfiguration configuration, QueueBrowseStrategy queueBrowseStrategy) {
        super(uri, component, destination, configuration, queueBrowseStrategy);
        setDestinationType("temp-queue");
    }

    public JmsTemporaryQueueEndpoint(String endpointUri, String destination) {
        super(endpointUri, destination);
        setDestinationType("temp-queue");
    }

    public JmsTemporaryQueueEndpoint(TemporaryQueue jmsDestination) throws JMSException {
        super("jms:temp-queue:" + jmsDestination.getQueueName(), null);
        setDestinationType("temp-queue");
        this.jmsDestination = jmsDestination;
        setDestination(jmsDestination);
    }


    /**
     * This endpoint is a singleton so that the temporary destination instances are shared across all
     * producers and consumers of the same endpoint URI
     *
     * @return true
     */
    public boolean isSingleton() {
        return true;
    }
    
    public synchronized Destination getJmsDestination(Session session) throws JMSException {
        if (jmsDestination == null) {
            jmsDestination = createJmsDestination(session);
        }
        return jmsDestination;
    }

    protected Destination createJmsDestination(Session session) throws JMSException {
        return session.createTemporaryQueue();
    }
}
