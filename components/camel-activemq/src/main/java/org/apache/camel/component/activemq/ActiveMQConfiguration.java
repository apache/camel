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

import java.lang.reflect.Constructor;

import javax.jms.ConnectionFactory;

import org.apache.activemq.Service;
import org.apache.activemq.spring.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsConfiguration;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 *
 */
public class ActiveMQConfiguration extends JmsConfiguration {
    private ActiveMQComponent activeMQComponent;
    private String brokerURL = ActiveMQConnectionFactory.DEFAULT_BROKER_URL;
    private boolean useSingleConnection;
    private boolean usePooledConnection = true;
    private boolean trustAllPackages;

    public ActiveMQConfiguration() {
    }

    public String getBrokerURL() {
        return brokerURL;
    }

    /**
     * Sets the broker URL to use to connect to ActiveMQ using the
     * <a href="http://activemq.apache.org/configuring-transports.html">ActiveMQ
     * URI format</a>
     *
     * @param brokerURL the URL of the broker.
     */
    public void setBrokerURL(String brokerURL) {
        this.brokerURL = brokerURL;
    }

    public boolean isUseSingleConnection() {
        return useSingleConnection;
    }

    /**
     * @deprecated - use JmsConfiguration#getUsername()
     * @see JmsConfiguration#getUsername()
     */
    @Deprecated
    public String getUserName() {
        return getUsername();
    }

    /**
     * @deprecated - use JmsConfiguration#setUsername(String)
     * @see JmsConfiguration#setUsername(String)
     */
    @Deprecated
    public void setUserName(String userName) {
        setUsername(userName);
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
        this.useSingleConnection = useSingleConnection;
    }

    public boolean isUsePooledConnection() {
        return usePooledConnection;
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
        this.usePooledConnection = usePooledConnection;
    }

    public boolean isTrustAllPackages() {
        return trustAllPackages;
    }

    /**
     * ObjectMessage objects depend on Java serialization of marshal/unmarshal
     * object payload. This process is generally considered unsafe as malicious
     * payload can exploit the host system. That's why starting with versions
     * 5.12.2 and 5.13.0, ActiveMQ enforces users to explicitly whitelist
     * packages that can be exchanged using ObjectMessages. <br/>
     * This option can be set to <tt>true</tt> to trust all packages (eg
     * whitelist is *).
     * <p/>
     * See more details at: http://activemq.apache.org/objectmessage.html
     */
    public void setTrustAllPackages(boolean trustAllPackages) {
        this.trustAllPackages = trustAllPackages;
    }

    /**
     * Factory method to create a default transaction manager if one is not
     * specified
     */
    @Override
    protected PlatformTransactionManager createTransactionManager() {
        JmsTransactionManager answer = new JmsTransactionManager(getConnectionFactory());
        answer.afterPropertiesSet();
        return answer;
    }

    protected void setActiveMQComponent(ActiveMQComponent activeMQComponent) {
        this.activeMQComponent = activeMQComponent;
    }

    @Override
    protected ConnectionFactory createConnectionFactory() {
        ActiveMQConnectionFactory answer = new ActiveMQConnectionFactory();
        answer.setTrustAllPackages(trustAllPackages);
        if (getUsername() != null) {
            answer.setUserName(getUsername());
        }
        if (getPassword() != null) {
            answer.setPassword(getPassword());
        }
        if (answer.getBeanName() == null) {
            answer.setBeanName("Camel");
        }
        answer.setBrokerURL(getBrokerURL());
        if (isUseSingleConnection()) {
            SingleConnectionFactory scf = new SingleConnectionFactory(answer);
            if (activeMQComponent != null) {
                activeMQComponent.addSingleConnectionFactory(scf);
            }
            return scf;
        } else if (isUsePooledConnection()) {
            ConnectionFactory pcf = createPooledConnectionFactory(answer);
            if (activeMQComponent != null) {
                activeMQComponent.addPooledConnectionFactoryService((Service)pcf);
            }
            return pcf;
        } else {
            return answer;
        }
    }

    protected ConnectionFactory createPooledConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
        // lets not use classes directly to avoid a runtime dependency on
        // commons-pool2
        // for folks not using this option
        try {
            Class type = loadClass("org.apache.activemq.pool.PooledConnectionFactory", getClass().getClassLoader());
            Constructor constructor = type.getConstructor(org.apache.activemq.ActiveMQConnectionFactory.class);
            return (ConnectionFactory)constructor.newInstance(connectionFactory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate PooledConnectionFactory: " + e, e);
        }
    }

    public static Class<?> loadClass(String name, ClassLoader loader) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    return loader.loadClass(name);
                } catch (ClassNotFoundException e1) {
                    throw e1;
                }
            }
        } else {
            return loader.loadClass(name);
        }
    }
}
