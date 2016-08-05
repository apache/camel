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
package org.apache.camel.component.amqp;

import java.net.MalformedURLException;
import java.util.Set;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.qpid.jms.JmsConnectionFactory;

/**
 * Messaging with AMQP protocol using Apache QPid Client.
 */
public class AMQPComponent extends JmsComponent {

    // Constructors

    public AMQPComponent() {
        super(AMQPEndpoint.class);
    }

    public AMQPComponent(JmsConfiguration configuration) {
        super(configuration);
    }

    public AMQPComponent(CamelContext context) {
        super(context, AMQPEndpoint.class);
    }

    public AMQPComponent(ConnectionFactory connectionFactory) {
        setConnectionFactory(connectionFactory);
    }

    // Life-cycle

    @Override
    protected void doStart() throws Exception {
        Set<AMQPConnectionDetails> connectionDetails = getCamelContext().getRegistry().findByType(AMQPConnectionDetails.class);
        if (connectionDetails.size() == 1) {
            AMQPConnectionDetails details = connectionDetails.iterator().next();
            JmsConnectionFactory connectionFactory = new JmsConnectionFactory(details.username(), details.password(), details.uri());
            connectionFactory.setTopicPrefix("topic://");
            setConnectionFactory(connectionFactory);
        }
        super.doStart();
    }

    // Factory methods

    /**
     * Use {@code amqpComponent(String uri)} instead.
     */
    @Deprecated
    public static AMQPComponent amqp10Component(String uri) throws MalformedURLException {
        JmsConnectionFactory connectionFactory = new JmsConnectionFactory(uri);
        connectionFactory.setTopicPrefix("topic://");
        return new AMQPComponent(connectionFactory);
    }

    public static AMQPComponent amqpComponent(String uri) {
        JmsConnectionFactory connectionFactory = new JmsConnectionFactory(uri);
        connectionFactory.setTopicPrefix("topic://");
        return new AMQPComponent(connectionFactory);
    }

    public static AMQPComponent amqpComponent(String uri, String username, String password) {
        JmsConnectionFactory connectionFactory = new JmsConnectionFactory(username, password, uri);
        connectionFactory.setTopicPrefix("topic://");
        return new AMQPComponent(connectionFactory);
    }

}
