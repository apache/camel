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

import java.util.Map;
import java.util.Set;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.qpid.jms.JmsConnectionFactory;

/**
 * Messaging with AMQP protocol using Apache QPid Client.
 */
@Component("amqp")
public class AMQPComponent extends JmsComponent {

    // Constructors

    public AMQPComponent() {
    }

    public AMQPComponent(JmsConfiguration configuration) {
        super(configuration);
    }

    public AMQPComponent(CamelContext context) {
        super(context);
    }

    public AMQPComponent(ConnectionFactory connectionFactory) {
        this();
        getConfiguration().setConnectionFactory(connectionFactory);
    }

    // Factory methods

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

    // Life-cycle

    @Override
    protected void doInit() throws Exception {
        Set<AMQPConnectionDetails> connectionDetails = getCamelContext().getRegistry().findByType(AMQPConnectionDetails.class);
        if (connectionDetails.size() == 1) {
            AMQPConnectionDetails details = connectionDetails.iterator().next();
            JmsConnectionFactory connectionFactory
                    = new JmsConnectionFactory(details.username(), details.password(), details.uri());
            if (details.setTopicPrefix()) {
                connectionFactory.setTopicPrefix("topic://");
            }
            getConfiguration().setConnectionFactory(connectionFactory);
        }
        super.doInit();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) super.createEndpoint(uri, remaining, parameters);
        endpoint.setBinding(new AMQPJmsBinding(endpoint));
        return endpoint;
    }

    /**
     * Factory method to create the default configuration instance
     *
     * @return a newly created configuration object which can then be further customized
     */
    @Override
    protected JmsConfiguration createConfiguration() {
        return new AMQPConfiguration();
    }

    // Properties

    /**
     * Whether to include AMQP annotations when mapping from AMQP to Camel Message. Setting this to true maps AMQP
     * message annotations that contain a JMS_AMQP_MA_ prefix to message headers. Due to limitations in Apache Qpid JMS
     * API, currently delivery annotations are ignored.
     */
    @Metadata(displayName = "Include AMQP Annotations")
    public void setIncludeAmqpAnnotations(boolean includeAmqpAnnotations) {
        if (getConfiguration() instanceof AMQPConfiguration amqpConfiguration) {
            amqpConfiguration.setIncludeAmqpAnnotations(includeAmqpAnnotations);
        }
    }

    public boolean isIncludeAmqpAnnotations() {
        if (getConfiguration() instanceof AMQPConfiguration amqpConfiguration) {
            return amqpConfiguration.isIncludeAmqpAnnotations();
        }
        return false;
    }

    @Override
    protected void setProperties(Endpoint bean, Map<String, Object> parameters) throws Exception {
        Object includeAmqpAnnotations = parameters.remove("includeAmqpAnnotations");
        if (includeAmqpAnnotations != null) {
            ((AMQPConfiguration) ((JmsEndpoint) bean).getConfiguration())
                    .setIncludeAmqpAnnotations(
                            PropertyConfigurerSupport.property(getCamelContext(), boolean.class, includeAmqpAnnotations));
        }
        super.setProperties(bean, parameters);
    }

}
