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

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jms.Connection;

import org.apache.activemq.EnhancedConnection;
import org.apache.activemq.Service;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * The <a href="http://activemq.apache.org/camel/activemq.html">ActiveMQ
 * Component</a>
 */
@Component("activemq")
public class ActiveMQComponent extends JmsComponent {
    private static final transient Logger LOG = LoggerFactory.getLogger(ActiveMQComponent.class);
    DestinationSource source;
    private boolean exposeAllQueues;
    private CamelEndpointLoader endpointLoader;
    private EnhancedConnection connection;
    private final CopyOnWriteArrayList<SingleConnectionFactory> singleConnectionFactoryList = new CopyOnWriteArrayList<SingleConnectionFactory>();
    private final CopyOnWriteArrayList<Service> pooledConnectionFactoryServiceList = new CopyOnWriteArrayList<Service>();
    
    public ActiveMQComponent() {
    }
    
    public ActiveMQComponent(CamelContext context) {
        super(context);
    }

    public ActiveMQComponent(ActiveMQConfiguration configuration) {
        super();
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

    /**
     * Sets the broker URL to use to connect to ActiveMQ using the
     * <a href="http://activemq.apache.org/configuring-transports.html">ActiveMQ
     * URI format</a>
     */
    public void setBrokerURL(String brokerURL) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setBrokerURL(brokerURL);
        }
    }

    /**
     * Define if all packages are trusted or not
     */
    public void setTrustAllPackages(boolean trustAllPackages) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setTrustAllPackages(trustAllPackages);
        }
    }

    public boolean isExposeAllQueues() {
        return exposeAllQueues;
    }

    /**
     * If enabled this will cause all Queues in the ActiveMQ broker to be
     * eagerly populated into the CamelContext so that they can be easily
     * browsed by any Camel tooling. This option is disabled by default.
     */
    public void setExposeAllQueues(boolean exposeAllQueues) {
        this.exposeAllQueues = exposeAllQueues;
    }

    /**
     * Enables or disables whether a PooledConnectionFactory will be used so
     * that when messages are sent to ActiveMQ from outside of a message
     * consuming thread, pooling will be used rather than the default with the
     * Spring {@link JmsTemplate} which will create a new connection, session,
     * producer for each message then close them all down again.
     * <p/>
     * The default value is true. Note that this requires an extra dependency on
     * commons-pool2.
     */
    public void setUsePooledConnection(boolean usePooledConnection) {
        if (getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)getConfiguration()).setUsePooledConnection(usePooledConnection);
        }
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
        Map options = IntrospectionSupport.extractProperties(parameters, "destination.");

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

        if (isExposeAllQueues()) {
            createDestinationSource();
            endpointLoader = new CamelEndpointLoader(getCamelContext(), source);
            endpointLoader.afterPropertiesSet();
        }

        // use OriginalDestinationPropagateStrategy by default if no custom
        // stategy has been set
        if (getMessageCreatedStrategy() == null) {
            setMessageCreatedStrategy(new OriginalDestinationPropagateStrategy());
        }
    }

    protected void createDestinationSource() {
        try {
            if (source == null) {
                if (connection == null) {
                    Connection value = getConfiguration().getConnectionFactory().createConnection();
                    if (value instanceof EnhancedConnection) {
                        connection = (EnhancedConnection)value;
                    } else {
                        throw new IllegalArgumentException("Created JMS Connection is not an EnhancedConnection: " + value);
                    }
                    connection.start();
                }
                source = connection.getDestinationSource();
            }
        } catch (Throwable t) {
            LOG.info("Can't get destination source, endpoint completer will not work", t);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (source != null) {
            source.stop();
            source = null;
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
        for (Service s : pooledConnectionFactoryServiceList) {
            s.stop();
        }
        pooledConnectionFactoryServiceList.clear();
        for (SingleConnectionFactory s : singleConnectionFactoryList) {
            s.destroy();
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
