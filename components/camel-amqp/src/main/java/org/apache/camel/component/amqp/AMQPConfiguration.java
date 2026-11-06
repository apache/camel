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
package org.apache.camel.component.amqp;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AMQPConfiguration extends JmsConfiguration {

    @UriParam(label = "consumer,advanced",
              description = "Whether to include AMQP annotations when mapping from AMQP to Camel Message."
                            + " Setting this to true maps AMQP message annotations that contain a JMS_AMQP_MA_ prefix to message headers."
                            + " Due to limitations in Apache Qpid JMS API, currently delivery annotations are ignored.")
    private boolean includeAmqpAnnotations;

    // set by AMQPComponent when it creates the connection factory, see AMQPComponent#setCacheConnection
    private ConnectionFactory cachedConnectionFactory;
    private ConnectionFactory connectionFactoryToCache;

    public AMQPConfiguration() {
    }

    public AMQPConfiguration(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public boolean isIncludeAmqpAnnotations() {
        return includeAmqpAnnotations;
    }

    /**
     * Whether to include AMQP annotations when mapping from AMQP to Camel Message. Setting this to true maps AMQP
     * message annotations that contain a JMS_AMQP_MA_ prefix to message headers. Due to limitations in Apache Qpid JMS
     * API, currently delivery annotations are ignored.
     */
    public void setIncludeAmqpAnnotations(boolean includeAmqpAnnotations) {
        this.includeAmqpAnnotations = includeAmqpAnnotations;
    }

    /**
     * The templates created for the given connection factory open their connections from the cached connection factory.
     * With null, all templates use their own connection factory.
     */
    void useCachedConnection(ConnectionFactory connectionFactory, ConnectionFactory cachedConnectionFactory) {
        this.connectionFactoryToCache = connectionFactory;
        this.cachedConnectionFactory = cachedConnectionFactory;
    }

    @Override
    protected CamelJmsTemplate createCamelJmsTemplate(ConnectionFactory connectionFactory) {
        if (cachedConnectionFactory != null && connectionFactory == connectionFactoryToCache) {
            return new CachedConnectionJmsTemplate(this, connectionFactory, cachedConnectionFactory);
        }
        return super.createCamelJmsTemplate(connectionFactory);
    }

    /**
     * A template that opens its connections from the cached connection factory. Its own connection factory stays the
     * one of the endpoint: Spring finds the session of a transaction, or of the consumer that runs the route, by that
     * connection factory, so a message sent in a transacted route takes part in the transaction as without the cache.
     */
    private static final class CachedConnectionJmsTemplate extends CamelJmsTemplate {

        private final ConnectionFactory cachedConnectionFactory;

        CachedConnectionJmsTemplate(JmsConfiguration config, ConnectionFactory connectionFactory,
                                    ConnectionFactory cachedConnectionFactory) {
            super(config, connectionFactory);
            this.cachedConnectionFactory = cachedConnectionFactory;
        }

        @Override
        protected Connection createConnection() throws JMSException {
            return cachedConnectionFactory.createConnection();
        }
    }

}
