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
package org.apache.camel.component.activemq;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.QueueBrowseStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.URISupport;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * The ActiveMQ Component.
 */
@Component("activemq")
public class ActiveMQComponent extends JmsComponent {
    private final CopyOnWriteArrayList<SingleConnectionFactory> singleConnectionFactoryList = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Object> pooledConnectionFactoryServiceList = new CopyOnWriteArrayList<>();

    public ActiveMQComponent() {
    }

    public ActiveMQComponent(CamelContext context) {
        super(context);
    }

    public ActiveMQComponent(ActiveMQConfiguration configuration) {
        setConfiguration(configuration);
    }

    /**
     * Creates an <a href="http://camel.apache.org/activemq.html">ActiveMQ Component</a>
     *
     * @return the created component
     */
    public static ActiveMQComponent activeMQComponent() {
        return new ActiveMQComponent();
    }

    /**
     * Creates an <a href="http://camel.apache.org/activemq.html">ActiveMQ Component</a> connecting to the given
     * <a href="http://activemq.apache.org/configuring-transports.html">broker URL</a>
     *
     * @param  brokerURL the URL to connect to
     * @return           the created component
     */
    public static ActiveMQComponent activeMQComponent(String brokerURL) {
        ActiveMQComponent answer = new ActiveMQComponent();
        if (answer.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration) answer.getConfiguration()).setBrokerURL(brokerURL);
        }

        return answer;
    }

    public String getBrokerURL() {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            return activeMQConfiguration.getBrokerURL();
        }
        return null;
    }

    /**
     * Sets the broker URL to use to connect to ActiveMQ. If none configured then localhost:61616 is used by default
     * (however can be overridden by configuration from environment variables)
     */
    @Metadata(label = "common")
    public void setBrokerURL(String brokerURL) {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            activeMQConfiguration.setBrokerURL(brokerURL);
        }
    }

    /**
     * Define if all Java packages are trusted or not (for Java object JMS message types). Notice its not recommended
     * practice to send Java serialized objects over network. Setting this to true can expose security risks, so use
     * this with care.
     */
    @Metadata(defaultValue = "false", label = "advanced")
    public void setTrustAllPackages(boolean trustAllPackages) {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            activeMQConfiguration.setTrustAllPackages(trustAllPackages);
        }
    }

    public boolean isTrustAllPackages() {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            return activeMQConfiguration.isTrustAllPackages();
        }
        return false;
    }

    /**
     * Enables or disables whether a PooledConnectionFactory will be used so that when messages are sent to ActiveMQ
     * from outside a message consuming thread, pooling will be used rather than the default with the Spring
     * {@link JmsTemplate} which will create a new connection, session, producer for each message then close them all
     * down again.
     * <p/>
     * The default value is true.
     */
    @Metadata(defaultValue = "true", label = "common")
    public void setUsePooledConnection(boolean usePooledConnection) {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            activeMQConfiguration.setUsePooledConnection(usePooledConnection);
        }
    }

    public boolean isUsePooledConnection() {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            return activeMQConfiguration.isUsePooledConnection();
        }
        return true;
    }

    /**
     * Enables or disables whether a Spring {@link SingleConnectionFactory} will be used so that when messages are sent
     * to ActiveMQ from outside a message consuming thread, pooling will be used rather than the default with the Spring
     * {@link JmsTemplate} which will create a new connection, session, producer for each message then close them all
     * down again.
     * <p/>
     * The default value is false and a pooled connection is used by default.
     */
    @Metadata(defaultValue = "false", label = "common")
    public void setUseSingleConnection(boolean useSingleConnection) {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            activeMQConfiguration.setUseSingleConnection(useSingleConnection);
        }
    }

    public boolean isUseSingleConnection() {
        if (getConfiguration() instanceof ActiveMQConfiguration activeMQConfiguration) {
            return activeMQConfiguration.isUseSingleConnection();
        }
        return false;
    }

    @Override
    protected void setProperties(Endpoint bean, Map<String, Object> parameters) throws Exception {
        Object useSingleConnection = parameters.remove("useSingleConnection");
        if (useSingleConnection != null) {
            ((ActiveMQConfiguration) ((JmsEndpoint) bean).getConfiguration())
                    .setUseSingleConnection(
                            PropertyConfigurerSupport.property(getCamelContext(), boolean.class, useSingleConnection));
        }
        Object usePooledConnection = parameters.remove("usePooledConnection");
        if (usePooledConnection != null) {
            ((ActiveMQConfiguration) ((JmsEndpoint) bean).getConfiguration())
                    .setUsePooledConnection(
                            PropertyConfigurerSupport.property(getCamelContext(), boolean.class, usePooledConnection));
        }
        super.setProperties(bean, parameters);
    }

    protected void addPooledConnectionFactoryService(Object pooledConnectionFactoryService) {
        pooledConnectionFactoryServiceList.add(pooledConnectionFactoryService);
    }

    protected void addSingleConnectionFactory(SingleConnectionFactory singleConnectionFactory) {
        singleConnectionFactoryList.add(singleConnectionFactory);
    }

    @Override
    protected String convertPathToActualDestination(String path, Map<String, Object> parameters) {
        // support ActiveMQ destination options using the destination. prefix
        // http://activemq.apache.org/destination-options.html
        Map<String, Object> options = PropertiesHelper.extractProperties(parameters, "destination.");

        String query = URISupport.createQueryString(options);

        // if we have destination options then append them to the destination
        // name
        if (ObjectHelper.isNotEmpty(query)) {
            return path + "?" + query;
        } else {
            return path;
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // use OriginalDestinationPropagateStrategy by default if no custom
        // strategy has been set
        if (getMessageCreatedStrategy() == null) {
            setMessageCreatedStrategy(new OriginalDestinationPropagateStrategy());
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (Object s : pooledConnectionFactoryServiceList) {
            try {
                // invoke stop method if exists
                Method m = s.getClass().getMethod("stop");
                org.apache.camel.support.ObjectHelper.invokeMethod(m, s);
            } catch (Exception e) {
                // ignore
            }
        }
        pooledConnectionFactoryServiceList.clear();

        for (SingleConnectionFactory s : singleConnectionFactoryList) {
            try {
                s.destroy();
            } catch (Exception e) {
                // ignore
            }
        }
        singleConnectionFactoryList.clear();

        super.doStop();
    }

    /**
     * Configuration of ActiveMQ
     */
    @Override
    public void setConfiguration(JmsConfiguration configuration) {
        if (configuration instanceof ActiveMQConfiguration activeMQConfiguration) {
            activeMQConfiguration.setActiveMQComponent(this);
        }
        super.setConfiguration(configuration);
    }

    @Override
    protected JmsConfiguration createConfiguration() {
        ActiveMQConfiguration answer = new ActiveMQConfiguration();
        answer.setActiveMQComponent(this);
        return answer;
    }

    @Override
    protected JmsEndpoint createTemporaryTopicEndpoint(
            String uri, JmsComponent component, String subject, JmsConfiguration configuration) {
        return new ActiveMQTemporaryTopicEndpoint(uri, component, subject, configuration);
    }

    @Override
    protected JmsEndpoint createTopicEndpoint(
            String uri, JmsComponent component, String subject, JmsConfiguration configuration) {
        return new ActiveMQEndpoint(uri, component, subject, true, configuration);
    }

    @Override
    protected JmsEndpoint createTemporaryQueueEndpoint(
            String uri, JmsComponent component, String subject, JmsConfiguration configuration,
            QueueBrowseStrategy queueBrowseStrategy) {
        return new ActiveMQTemporaryQueueEndpoint(uri, component, subject, configuration, queueBrowseStrategy);
    }

    @Override
    protected JmsEndpoint createQueueEndpoint(
            String uri, JmsComponent component, String subject, JmsConfiguration configuration,
            QueueBrowseStrategy queueBrowseStrategy) {
        return new ActiveMQQueueEndpoint(uri, component, subject, configuration, queueBrowseStrategy);
    }
}
