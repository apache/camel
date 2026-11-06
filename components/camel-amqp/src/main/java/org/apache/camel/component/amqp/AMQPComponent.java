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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;

/**
 * Messaging with AMQP protocol using Apache Qpid Client.
 */
@Component("amqp")
public class AMQPComponent extends JmsComponent {

    private static final Logger LOG = LoggerFactory.getLogger(AMQPComponent.class);

    public static final String AMQP_DEFAULT_HOST = "localhost";
    public static final int AMQP_DEFAULT_PORT = 5672;

    @Metadata(description = "Remote URL to broker. The URL is used to setup connection factory and is broker specific (such as ActiveMQ).")
    private String brokerUrl;
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
    @Metadata(label = "security", security = "secret", description = "The SSL keystore password.")
    private String keyStorePassword;
    @Metadata(description = "The SSL truststore location.")
    private String trustStoreLocation;
    @Metadata(defaultValue = "JKS", description = "The SSL truststore type.")
    private String trustStoreType = "JKS";
    @Metadata(label = "security", security = "secret", description = "The SSL truststore password.")
    private String trustStorePassword;
    @Metadata(defaultValue = "true",
              description = "Whether the producers send over one shared connection to the broker, when the component creates"
                            + " the connection factory: from brokerUrl, from the host, port, username, password and useSsl"
                            + " options, or with the amqpComponent factory methods. Turn it off to open and close a connection"
                            + " for every message sent. The consumers keep their own connections. A connectionFactory that you"
                            + " set yourself, on the component or on the endpoint, is used as it is.")
    private boolean cacheConnection = true;

    // the connection factory that the component created, and the cache of its connection for the producers
    private ConnectionFactory createdConnectionFactory;
    private CachingConnectionFactory cachedConnectionFactory;

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
        return amqpComponent(connectionFactory);
    }

    public static AMQPComponent amqpComponent(String uri, String username, String password) {
        JmsConnectionFactory connectionFactory = new JmsConnectionFactory(username, password, uri);
        connectionFactory.setTopicPrefix("topic://");
        return amqpComponent(connectionFactory);
    }

    private static AMQPComponent amqpComponent(JmsConnectionFactory connectionFactory) {
        AMQPComponent answer = new AMQPComponent(connectionFactory);
        answer.createdConnectionFactory = connectionFactory;
        return answer;
    }

    // Life-cycle

    @Override
    protected void doInit() throws Exception {
        if (brokerUrl != null) {
            JmsConnectionFactory connectionFactory
                    = new JmsConnectionFactory(brokerUrl);
            if (useTopicPrefix != Boolean.FALSE) {
                connectionFactory.setTopicPrefix("topic://");
            }
            getConfiguration().setConnectionFactory(connectionFactory);
            createdConnectionFactory = connectionFactory;
        } else if (getConfiguration().getConnectionFactory() == null
                && (host != null || port != null || getUsername() != null || getPassword() != null || useTopicPrefix != null
                        || useSsl != null)) {
            StringBuilder sb = new StringBuilder();
            sb.append(useSsl == Boolean.TRUE ? "amqps://" : "amqp://");
            sb.append(host == null ? AMQP_DEFAULT_HOST : host).append(":").append(port == null ? AMQP_DEFAULT_PORT : port);
            if (useSsl == Boolean.TRUE) {
                boolean first = true;
                if (trustStoreLocation != null) {
                    sb.append('?');
                    first = false;
                    sb.append("transport.trustStoreLocation=").append(urlEncode(trustStoreLocation));
                    sb.append("&transport.trustStoreType=").append(urlEncode(trustStoreType));
                    if (trustStorePassword != null) {
                        sb.append("&transport.trustStorePassword=").append(urlEncode(trustStorePassword));
                    }
                }
                if (keyStoreLocation != null) {
                    sb.append(first ? '?' : '&');
                    sb.append("transport.keyStoreLocation=").append(urlEncode(keyStoreLocation));
                    sb.append("&transport.keyStoreType=").append(urlEncode(keyStoreType));
                    if (keyStorePassword != null) {
                        sb.append("&transport.keyStorePassword=").append(urlEncode(keyStorePassword));
                    }
                }
            }
            JmsConnectionFactory connectionFactory
                    = new JmsConnectionFactory(getUsername(), getPassword(), sb.toString());
            if (useTopicPrefix != Boolean.FALSE) {
                connectionFactory.setTopicPrefix("topic://");
            }
            getConfiguration().setConnectionFactory(connectionFactory);
            createdConnectionFactory = connectionFactory;
        } else {
            Set<AMQPConnectionDetails> connectionDetails
                    = getCamelContext().getRegistry().findByType(AMQPConnectionDetails.class);
            if (connectionDetails.size() == 1) {
                LOG.warn("Using AMQPConnectionDetails is deprecated");
                AMQPConnectionDetails details = connectionDetails.iterator().next();
                JmsConnectionFactory connectionFactory
                        = new JmsConnectionFactory(details.username(), details.password(), details.uri());
                if (details.setTopicPrefix()) {
                    connectionFactory.setTopicPrefix("topic://");
                }
                getConfiguration().setConnectionFactory(connectionFactory);
                createdConnectionFactory = connectionFactory;
            }
        }
        if (cacheConnection && createdConnectionFactory != null
                && createdConnectionFactory == getConfiguration().getConnectionFactory()
                && getConfiguration() instanceof AMQPConfiguration configuration) {
            cachedConnectionFactory = createCachedConnectionFactory(createdConnectionFactory);
            configuration.useCachedConnection(createdConnectionFactory, cachedConnectionFactory);
        }
        super.doInit();
    }

    private CachingConnectionFactory createCachedConnectionFactory(ConnectionFactory connectionFactory) {
        ConnectionFactory target = connectionFactory;
        if (getUsername() != null && getPassword() != null) {
            // as the endpoints do, see JmsComponent#createEndpoint
            UserCredentialsConnectionFactoryAdapter adapter = new UserCredentialsConnectionFactoryAdapter();
            adapter.setTargetConnectionFactory(connectionFactory);
            adapter.setUsername(getUsername());
            adapter.setPassword(getPassword());
            target = adapter;
        }
        CachingConnectionFactory answer = new CachingConnectionFactory(target);
        // a polling consumer (such as pollEnrich) closes its consumer after each receive, so it does not hold on to
        // the messages that the broker sends it ahead
        answer.setCacheConsumers(false);
        // a producer is created for each message, so the next message does not use a producer the broker has closed
        answer.setCacheProducers(false);
        return answer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // a connection factory of the endpoint, in any case as the endpoint options are not case sensitive
        boolean endpointConnectionFactory = parameters.keySet().stream().anyMatch("connectionFactory"::equalsIgnoreCase);
        JmsEndpoint endpoint = (JmsEndpoint) super.createEndpoint(uri, remaining, parameters);
        endpoint.setBinding(new AMQPJmsBinding(endpoint));
        if (cachedConnectionFactory != null && endpoint.getConfiguration() instanceof AMQPConfiguration configuration) {
            // the endpoint uses the connection factory that the component created, or the adapter that
            // JmsComponent#createEndpoint wraps around it with the credentials of the component
            ConnectionFactory connectionFactory = configuration.getConnectionFactory();
            boolean createdConnectionFactoryUsed = !endpointConnectionFactory
                    && getConfiguration().getConnectionFactory() == createdConnectionFactory
                    && (connectionFactory == createdConnectionFactory
                            || connectionFactory instanceof UserCredentialsConnectionFactoryAdapter)
                    && Objects.equals(configuration.getUsername(), getUsername())
                    && Objects.equals(configuration.getPassword(), getPassword());
            if (createdConnectionFactoryUsed) {
                configuration.useCachedConnection(connectionFactory, cachedConnectionFactory);
            } else {
                configuration.useCachedConnection(null, null);
            }
        }
        return endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        if (cachedConnectionFactory != null) {
            // closes the shared connection, which opens again on the next message sent
            cachedConnectionFactory.resetConnection();
        }
        super.doStop();
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

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
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

    public boolean isCacheConnection() {
        return cacheConnection;
    }

    public void setCacheConnection(boolean cacheConnection) {
        this.cacheConnection = cacheConnection;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
