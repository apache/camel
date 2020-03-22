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

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.activemq.Service;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsEndpoint;
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
    private final CopyOnWriteArrayList<Service> pooledConnectionFactoryServiceList = new CopyOnWriteArrayList<>();

    public ActiveMQComponent() {
    }
    
    public ActiveMQComponent(CamelContext context) {
        super(context);
    }

    public ActiveMQComponent(ActiveMQConfiguration configuration) {
        setConfiguration(configuration);
    }

    /**
     * Creates an <a href="http://camel.apache.org/activemq.html">ActiveMQ
     * Component</a>
     *
     * @return the created component
     */
    public static ActiveMQComponent activeMQComponent() {
        return new ActiveMQComponent();
    }

    /**
     * Creates an <a href="http://camel.apache.org/activemq.html">ActiveMQ
     * Component</a> connecting to the given
     * <a href="http://activemq.apache.org/configuring-transports.html">broker
     * URL</a>
     *
     * @param brokerURL the URL to connect to
     * @return the created component
     */
    public static ActiveMQComponent activeMQComponent(String brokerURL) {
        ActiveMQComponent answer = new ActiveMQComponent();
        if (answer.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)answer.getConfiguration()).setBrokerURL(brokerURL);
        }

        return answer;
    }

    public String getBrokerURL() {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            return ((ActiveMQConfiguration) getConfiguration()).getBrokerURL();
        }
        return null;
    }

    /**
     * Sets the broker URL to use to connect to ActiveMQ
     */
    public void setBrokerURL(String brokerURL) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setBrokerURL(brokerURL);
        }
    }

    /**
     * Define if all Java packages are trusted or not (for Java object JMS message types).
     * Notice its not recommended practice to send Java serialized objects over network.
     * Setting this to true can expose security risks, so use this with care.
     */
    public void setTrustAllPackages(boolean trustAllPackages) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setTrustAllPackages(trustAllPackages);
        }
    }

    public boolean isTrustAllPackages() {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            return ((ActiveMQConfiguration)getConfiguration()).isTrustAllPackages();
        }
        return false;
    }

    /**
     * Enables or disables whether a PooledConnectionFactory will be used so
     * that when messages are sent to ActiveMQ from outside of a message
     * consuming thread, pooling will be used rather than the default with the
     * Spring {@link JmsTemplate} which will create a new connection, session,
     * producer for each message then close them all down again.
     * <p/>
     * The default value is true.
     */
    public void setUsePooledConnection(boolean usePooledConnection) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setUsePooledConnection(usePooledConnection);
        }
    }

    public boolean isUsePooledConnection() {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            return ((ActiveMQConfiguration)getConfiguration()).isUsePooledConnection();
        }
        return true;
    }

    /**
     * Enables or disables whether a Spring {@link SingleConnectionFactory} will
     * be used so that when messages are sent to ActiveMQ from outside of a
     * message consuming thread, pooling will be used rather than the default
     * with the Spring {@link JmsTemplate} which will create a new connection,
     * session, producer for each message then close them all down again.
     * <p/>
     * The default value is false and a pooled connection is used by default.
     */
    public void setUseSingleConnection(boolean useSingleConnection) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setUseSingleConnection(useSingleConnection);
        }
    }

    public boolean isUseSingleConnection() {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            return ((ActiveMQConfiguration)getConfiguration()).isUseSingleConnection();
        }
        return false;
    }

    @Override
    protected void setProperties(Endpoint bean, Map<String, Object> parameters) throws Exception {
        Object useSingleConnection = parameters.remove("useSingleConnection");
        if (useSingleConnection != null) {
            ((ActiveMQConfiguration) ((JmsEndpoint) bean).getConfiguration())
                    .setUseSingleConnection(PropertyConfigurerSupport.property(getCamelContext(), boolean.class, useSingleConnection));
        }
        Object usePooledConnection = parameters.remove("usePooledConnection");
        if (usePooledConnection != null) {
            ((ActiveMQConfiguration) ((JmsEndpoint) bean).getConfiguration())
                    .setUsePooledConnection(PropertyConfigurerSupport.property(getCamelContext(), boolean.class, usePooledConnection));
        }
        super.setProperties(bean, parameters);
    }

    protected void addPooledConnectionFactoryService(Service pooledConnectionFactoryService) {
        pooledConnectionFactoryServiceList.add(pooledConnectionFactoryService);
    }

    protected void addSingleConnectionFactory(SingleConnectionFactory singleConnectionFactory) {
        singleConnectionFactoryList.add(singleConnectionFactory);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String convertPathToActualDestination(String path, Map<String, Object> parameters) {
        // support ActiveMQ destination options using the destination. prefix
        // http://activemq.apache.org/destination-options.html
        Map options = PropertiesHelper.extractProperties(parameters, "destination.");

        String query;
        try {
            query = URISupport.createQueryString(options);
        } catch (URISyntaxException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        // if we have destination options then append them to the destination
        // name
        if (ObjectHelper.isNotEmpty(query)) {
            return path + "?" + query;
        } else {
            return path;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // use OriginalDestinationPropagateStrategy by default if no custom
        // strategy has been set
        if (getMessageCreatedStrategy() == null) {
            setMessageCreatedStrategy(new OriginalDestinationPropagateStrategy());
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (Service s : pooledConnectionFactoryServiceList) {
            try {
                s.stop();
            } catch (Throwable e) {
                // ignore
            }
        }
        pooledConnectionFactoryServiceList.clear();

        for (SingleConnectionFactory s : singleConnectionFactoryList) {
            try {
                s.destroy();
            } catch (Throwable e) {
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
        if (configuration instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)configuration).setActiveMQComponent(this);
        }
        super.setConfiguration(configuration);
    }

    @Override
    protected JmsConfiguration createConfiguration() {
        ActiveMQConfiguration answer = new ActiveMQConfiguration();
        answer.setActiveMQComponent(this);
        return answer;
    }

}
