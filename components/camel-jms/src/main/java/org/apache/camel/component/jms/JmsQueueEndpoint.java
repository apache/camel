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

import java.util.Collections;
import java.util.List;
import javax.jms.JMSException;
import javax.jms.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.BrowsableEndpoint;
import org.springframework.jms.core.JmsOperations;

/**
 * An endpoint for a JMS Queue which is also browsable
 *
 * @version 
 */
@ManagedResource(description = "Managed JMS Queue Endpoint")
public class JmsQueueEndpoint extends JmsEndpoint implements BrowsableEndpoint {
    private int maximumBrowseSize = -1;
    private final QueueBrowseStrategy queueBrowseStrategy;

    public JmsQueueEndpoint(Queue destination) throws JMSException {
        this("jms:queue:" + destination.getQueueName(), null);
        setDestinationType("queue");
        setDestination(destination);
    }
    
    public JmsQueueEndpoint(String uri, JmsComponent component, String destination,
            JmsConfiguration configuration) {
        this(uri, component, destination, configuration, null);
        setDestinationType("queue");
    }

    public JmsQueueEndpoint(String uri, JmsComponent component, String destination,
            JmsConfiguration configuration, QueueBrowseStrategy queueBrowseStrategy) {
        super(uri, component, destination, false, configuration);
        setDestinationType("queue");
        if (queueBrowseStrategy == null) {
            this.queueBrowseStrategy = createQueueBrowseStrategy();
        } else {
            this.queueBrowseStrategy = queueBrowseStrategy;
        }
    }

    public JmsQueueEndpoint(String endpointUri, String destination, QueueBrowseStrategy queueBrowseStrategy) {
        super(endpointUri, destination, false);
        setDestinationType("queue");
        if (queueBrowseStrategy == null) {
            this.queueBrowseStrategy = createQueueBrowseStrategy();
        } else {
            this.queueBrowseStrategy = queueBrowseStrategy;
        }
    }

    public JmsQueueEndpoint(String endpointUri, String destination) {
        super(endpointUri, destination, false);
        setDestinationType("queue");
        queueBrowseStrategy = createQueueBrowseStrategy();
    }

    @ManagedAttribute
    public int getMaximumBrowseSize() {
        return maximumBrowseSize;
    }

    /**
     * If a number is set > 0 then this limits the number of messages that are
     * returned when browsing the queue
     */
    @ManagedAttribute
    public void setMaximumBrowseSize(int maximumBrowseSize) {
        this.maximumBrowseSize = maximumBrowseSize;
    }

    public List<Exchange> getExchanges() {
        if (queueBrowseStrategy == null) {
            return Collections.emptyList();
        }
        String queue = getDestinationName();
        JmsOperations template = getConfiguration().createInOnlyTemplate(this, false, queue);
        return queueBrowseStrategy.browse(template, queue, this);
    }

    protected QueueBrowseStrategy createQueueBrowseStrategy() {
        return new DefaultQueueBrowseStrategy();
    }

}
