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

    public static final String AMQP_DEFAULT_HOST = "localhost";
    public static final int AMQP_DEFAULT_PORT = 5672;

    @Metadata(description = "The host name or IP address of the computer that hosts the AMQP Broker.")
    private String host;
    @Metadata(description = "The port number on which the AMPQ Broker listens.")
    private Integer port;
    @Metadata(description = "Whether to configure topics with a `topic://` prefix.")
    private Boolean useTopicPrefix;
    @Metadata(description = "Whether to enable SSL when connecting to the AMQP Broker.")
    private Boolean useSsl;
    @Metadata(description = "The SSL keystore location.")
    private String keyStoreLocation;
    @Metadata(defaultValue = "JKS", description = "The SSL keystore type.")
    private String keyStoreType = "JKS";
    @Metadata(label = "security", secret = true, description = "The SSL keystore password.")
    private String keyStorePassword;
    @Metadata(description = "The SSL truststore location.")
    private String trustStoreLocation;
    @Metadata(defaultValue = "JKS", description = "The SSL truststore type.")
    private String trustStoreType = "JKS";
    @Metadata(label = "security", secret = true, description = "The SSL truststore password.")
    private String trustStorePassword;

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
        if (host != null || port != null || getUsername() != null || getPassword() != null || useTopicPrefix != null
                || useSsl != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(useSsl == Boolean.TRUE ? "amqps://" : "amqp://");
            sb.append(host == null ? AMQP_DEFAULT_HOST : host).append(":").append(port == null ? AMQP_DEFAULT_PORT : port);
            if (useSsl == Boolean.TRUE) {
                sb.append("?transport.trustStoreLocation=").append(trustStoreLocation == null ? "" : trustStoreLocation);
                sb.append("&transport.trustStoreType=").append(trustStoreType);
                sb.append("&transport.trustStorePassword=").append(trustStorePassword == null ? "" : trustStorePassword);
                sb.append("&transport.keyStoreLocation=").append(keyStoreLocation == null ? "" : keyStoreLocation);
                sb.append("&transport.keyStoreType=").append(keyStoreType);
                sb.append("&transport.keyStorePassword=").append(keyStorePassword == null ? "" : keyStorePassword);
            }
            JmsConnectionFactory connectionFactory
                    = new JmsConnectionFactory(getUsername(), getPassword(), sb.toString());
            if (useTopicPrefix != Boolean.FALSE) {
                connectionFactory.setTopicPrefix("topic://");
            }
            getConfiguration().setConnectionFactory(connectionFactory);
        } else {
            Set<AMQPConnectionDetails> connectionDetails
                    = getCamelContext().getRegistry().findByType(AMQPConnectionDetails.class);
            if (connectionDetails.size() == 1) {
                AMQPConnectionDetails details = connectionDetails.iterator().next();
                JmsConnectionFactory connectionFactory
                        = new JmsConnectionFactory(details.username(), details.password(), details.uri());
                if (details.setTopicPrefix()) {
                    connectionFactory.setTopicPrefix("topic://");
                }
                getConfiguration().setConnectionFactory(connectionFactory);
            }
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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getUseTopicPrefix() {
        return useTopicPrefix;
    }

    public void setUseTopicPrefix(Boolean useTopicPrefix) {
        this.useTopicPrefix = useTopicPrefix;
    }

    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getKeyStoreLocation() {
        return keyStoreLocation;
    }

    public void setKeyStoreLocation(String keyStoreLocation) {
        this.keyStoreLocation = keyStoreLocation;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getTrustStoreLocation() {
        return trustStoreLocation;
    }

    public void setTrustStoreLocation(String trustStoreLocation) {
        this.trustStoreLocation = trustStoreLocation;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }
}
