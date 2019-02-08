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
import javax.jms.Queue;
import javax.jms.QueueReceiver;

import org.apache.activemq.ActiveMQSession;
import org.apache.camel.Endpoint;

/**
 * A JMS {@link javax.jms.QueueReceiver} which consumes message exchanges from a
 * Camel {@link org.apache.camel.Endpoint}
 */
public class CamelQueueReceiver extends CamelMessageConsumer implements QueueReceiver {

    public CamelQueueReceiver(CamelQueue destination, Endpoint endpoint, ActiveMQSession session, String name) {
        super(destination, endpoint, session, null, false);
    }

    /**
     * Gets the <CODE>Queue</CODE> associated with this queue receiver.
     *
     * @return this receiver's <CODE>Queue</CODE>
     * @throws JMSException if the JMS provider fails to get the queue for this
     *             queue receiver due to some internal error.
     */

    public Queue getQueue() throws JMSException {
        checkClosed();
        return (Queue)super.getDestination();
    }
}
