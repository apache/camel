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

import java.lang.reflect.Constructor;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.support.ObjectHelper;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.DelegatingConnectionFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public class ActiveMQConfiguration extends JmsConfiguration {
    private ActiveMQComponent activeMQComponent;
    private String brokerURL = ActiveMQConnectionFactory.DEFAULT_BROKER_URL;
    private volatile boolean customBrokerURL;
    private boolean useSingleConnection;
    private boolean usePooledConnection = true;
    private boolean trustAllPackages;

    public ActiveMQConfiguration() {
    }

    public String getBrokerURL() {
        return brokerURL;
    }

    /**
     * Sets the broker URL to use to connect to ActiveMQ broker.
     */
    public void setBrokerURL(String brokerURL) {
        this.brokerURL = brokerURL;
        this.customBrokerURL = true;
    }

    public boolean isUseSingleConnection() {
        return useSingleConnection;
    }

    /**
     * @see        JmsConfiguration#getUsername()
     * @deprecated - use JmsConfiguration#getUsername()
     */
    @Deprecated
    public String getUserName() {
        return getUsername();
    }

    /**
     * @see        JmsConfiguration#setUsername(String)
     * @deprecated - use JmsConfiguration#setUsername(String)
     */
    @Deprecated
    public void setUserName(String userName) {
        setUsername(userName);
    }

    /**
     * Enables or disables whether a Spring {@link SingleConnectionFactory} will be used so that when messages are sent
     * to ActiveMQ from outside a message consuming thread, pooling will be used rather than the default with the Spring
     * {@link JmsTemplate} which will create a new connection, session, producer for each message then close them all
     * down again.
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
     * Enables or disables whether a PooledConnectionFactory will be used so that when messages are sent to ActiveMQ
     * from outside of a message consuming thread, pooling will be used rather than the default with the Spring
     * {@link JmsTemplate} which will create a new connection, session, producer for each message then close them all
     * down again.
     * <p/>
     * The default value is true.
     */
    public void setUsePooledConnection(boolean usePooledConnection) {
        this.usePooledConnection = usePooledConnection;
    }

    public boolean isTrustAllPackages() {
        return trustAllPackages;
    }

    /**
     * ObjectMessage objects depend on Java serialization of marshal/unmarshal object payload. This process is generally
     * considered unsafe as malicious payload can exploit the host system. That's why starting with versions 5.12.2 and
     * 5.13.0, ActiveMQ enforces users to explicitly whitelist packages that can be exchanged using ObjectMessages.
     * <br/>
     * This option can be set to <tt>true</tt> to trust all packages (eg whitelist is *).
     * <p/>
     * See more details at:
     * <a href="http://activemq.apache.org/objectmessage.html">http://activemq.apache.org/objectmessage.html</a>
     */
    public void setTrustAllPackages(boolean trustAllPackages) {
        this.trustAllPackages = trustAllPackages;
    }

    /**
     * Factory method to create a default transaction manager if one is not specified
     */
    @Override
    protected PlatformTransactionManager createTransactionManager() {
        JmsTransactionManager answer = new JmsTransactionManager(getOrCreateConnectionFactory());
        answer.afterPropertiesSet();
        return answer;
    }

    protected void setActiveMQComponent(ActiveMQComponent activeMQComponent) {
        this.activeMQComponent = activeMQComponent;
    }

    @Override
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        ActiveMQConnectionFactory acf = null;

        ConnectionFactory target = connectionFactory;
        if (target instanceof CachingConnectionFactory ccf) {
            target = ccf.getTargetConnectionFactory();
        }
        if (target instanceof DelegatingConnectionFactory dcf) {
            target = dcf.getTargetConnectionFactory();
        }
        if (target instanceof ActiveMQConnectionFactory) {
            acf = (ActiveMQConnectionFactory) target;
        }

        if (acf != null) {
            if (customBrokerURL) {
                // okay a custom broker url was configured which we want to ensure
                // the real target connection factory knows about
                acf.setBrokerURL(brokerURL);
            } else {
                // its the opposite the target has the brokerURL which we want to set on this
                setBrokerURL(acf.getBrokerURL());
            }
        }

        super.setConnectionFactory(connectionFactory);
    }

    @Override
    protected ConnectionFactory createConnectionFactory() {
        org.apache.activemq.ActiveMQConnectionFactory answer
                = new org.apache.activemq.ActiveMQConnectionFactory();
        answer.setTrustAllPackages(trustAllPackages);
        if (getUsername() != null) {
            answer.setUserName(getUsername());
        }
        if (getPassword() != null) {
            answer.setPassword(getPassword());
        }
        answer.setBrokerURL(getBrokerURL());
        CamelContext context = activeMQComponent != null ? activeMQComponent.getCamelContext() : null;
        if (isUseSingleConnection()) {
            SingleConnectionFactory scf = new SingleConnectionFactory(answer);
            if (activeMQComponent != null) {
                activeMQComponent.addSingleConnectionFactory(scf);
            }
            return scf;
        } else if (isUsePooledConnection()) {
            ConnectionFactory pcf = createPooledConnectionFactory(context, answer);
            if (activeMQComponent != null) {
                activeMQComponent.addPooledConnectionFactoryService(pcf);
            }
            return pcf;
        } else {
            return answer;
        }
    }

    protected ConnectionFactory createPooledConnectionFactory(
            CamelContext camelContext, ActiveMQConnectionFactory connectionFactory) {
        try {
            Class<?> type = loadClass(camelContext, "org.messaginghub.pooled.jms.JmsPoolConnectionFactory",
                    getClass().getClassLoader());

            Constructor<?> constructor = type.getConstructor();
            ConnectionFactory cf = (ConnectionFactory) constructor.newInstance();
            ObjectHelper.invokeMethod(type.getDeclaredMethod("setConnectionFactory", Object.class), cf,
                    connectionFactory);
            return cf;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to instantiate PooledConnectionFactory: " + e, e);
        }
    }

    public static Class<?> loadClass(CamelContext camelContext, String name, ClassLoader loader) throws ClassNotFoundException {
        // if camel then use it to load the class
        if (camelContext != null) {
            return camelContext.getClassResolver()
                    .resolveMandatoryClass("org.messaginghub.pooled.jms.JmsPoolConnectionFactory");
        }

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                return loader.loadClass(name);
            }
        } else {
            return loader.loadClass(name);
        }
    }
}
