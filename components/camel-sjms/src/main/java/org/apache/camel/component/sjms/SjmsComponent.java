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
package org.apache.camel.component.sjms;

import java.util.Map;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sjms.jms.ConnectionFactoryResource;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.KeyFormatStrategy;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/sjms">Simple JMS</a> component.
 */
public class SjmsComponent extends UriEndpointComponent implements HeaderFilterStrategyAware {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(SjmsComponent.class);

    private ConnectionFactory connectionFactory;
    private ConnectionResource connectionResource;
    private HeaderFilterStrategy headerFilterStrategy = new SjmsHeaderFilterStrategy();
    private KeyFormatStrategy keyFormatStrategy;
    private Integer connectionCount = 1;
    private TransactionCommitStrategy transactionCommitStrategy;
    private TimedTaskManager timedTaskManager;

    public SjmsComponent() {
        super(SjmsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        validateMepAndReplyTo(parameters);
        uri = normalizeUri(uri);
        SjmsEndpoint endpoint = new SjmsEndpoint(uri, this);
        setProperties(endpoint, parameters);
        if (endpoint.isTransacted()) {
            endpoint.setSynchronous(true);
        }
        if (transactionCommitStrategy != null) {
            endpoint.setTransactionCommitStrategy(transactionCommitStrategy);
        }
        return endpoint;
    }

    /**
     * Helper method used to detect the type of endpoint and add the "queue"
     * protocol if it is a default endpoint URI.
     * 
     * @param uri The value passed into our call to create an endpoint
     * @return String
     * @throws Exception
     */
    private static String normalizeUri(String uri) throws Exception {
        String tempUri = uri;
        String endpointName = tempUri.substring(0, tempUri.indexOf(":"));
        tempUri = tempUri.substring(endpointName.length());
        if (tempUri.startsWith("://")) {
            tempUri = tempUri.substring(3);
        }
        String protocol = null;
        if (tempUri.indexOf(":") > 0) {
            protocol = tempUri.substring(0, tempUri.indexOf(":"));
        }
        if (ObjectHelper.isEmpty(protocol)) {
            protocol = "queue";
        } else if (protocol != null && (protocol.equals("queue") || protocol.equals("topic"))) {
            tempUri = tempUri.substring(protocol.length() + 1);
        } else {
            throw new Exception("Unsupported Protocol: " + protocol);
        }

        String path = tempUri;
        uri = endpointName + "://" + protocol + ":" + path;
        return uri;
    }

    /**
     * Helper method used to verify that when there is a namedReplyTo value we
     * are using the InOut MEP. If namedReplyTo is defined and the MEP is InOnly
     * the endpoint won't be expecting a reply so throw an error to alert the
     * user.
     * 
     * @param parameters {@link Endpoint} parameters
     * @throws Exception throws a {@link CamelException} when MEP equals InOnly
     *             and namedReplyTo is defined.
     */
    private static void validateMepAndReplyTo(Map<String, Object> parameters) throws Exception {
        boolean namedReplyToSet = parameters.containsKey("namedReplyTo");
        boolean mepSet = parameters.containsKey("exchangePattern");
        if (namedReplyToSet && mepSet) {
            if (!parameters.get("exchangePattern").equals(ExchangePattern.InOut.toString())) {
                String namedReplyTo = (String)parameters.get("namedReplyTo");
                ExchangePattern mep = ExchangePattern.valueOf((String)parameters.get("exchangePattern"));
                throw new CamelException("Setting parameter namedReplyTo=" + namedReplyTo + " requires a MEP of type InOut. Parameter exchangePattern is set to " + mep);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        timedTaskManager = new TimedTaskManager();

        LOGGER.trace("Verify ConnectionResource");
        if (getConnectionResource() == null) {
            LOGGER.debug("No ConnectionResource provided. Initialize the ConnectionFactoryResource.");
            // We always use a connection pool, even for a pool of 1
            ConnectionFactoryResource connections = new ConnectionFactoryResource(getConnectionCount(), getConnectionFactory());
            connections.fillPool();
            setConnectionResource(connections);
        } else if (getConnectionResource() instanceof ConnectionFactoryResource) {
            ((ConnectionFactoryResource)getConnectionResource()).fillPool();
        }
    }

    @Override
    protected void doStop() throws Exception {
        timedTaskManager.cancelTasks();

        if (getConnectionResource() != null) {
            if (getConnectionResource() instanceof ConnectionFactoryResource) {
                ((ConnectionFactoryResource)getConnectionResource()).drainPool();
            }
        }
        super.doStop();
    }

    /**
     * Sets the ConnectionFactory value of connectionFactory for this instance
     * of SjmsComponent.
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Gets the ConnectionFactory value of connectionFactory for this instance
     * of SjmsComponent.
     * 
     * @return the connectionFactory
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return this.headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public void setConnectionResource(ConnectionResource connectionResource) {
        this.connectionResource = connectionResource;
    }

    public ConnectionResource getConnectionResource() {
        return connectionResource;
    }

    public void setConnectionCount(Integer maxConnections) {
        this.connectionCount = maxConnections;
    }

    public Integer getConnectionCount() {
        return connectionCount;
    }

    public void setKeyFormatStrategy(KeyFormatStrategy keyFormatStrategy) {
        this.keyFormatStrategy = keyFormatStrategy;
    }

    public KeyFormatStrategy getKeyFormatStrategy() {
        return keyFormatStrategy;
    }

    /**
     * Gets the TransactionCommitStrategy value of transactionCommitStrategy for this
     * instance of SjmsComponent.
     * 
     * @return the transactionCommitStrategy
     */
    public TransactionCommitStrategy getTransactionCommitStrategy() {
        return transactionCommitStrategy;
    }

    /**
     * Sets the TransactionCommitStrategy value of transactionCommitStrategy for this
     * instance of SjmsComponent.
     */
    public void setTransactionCommitStrategy(TransactionCommitStrategy commitStrategy) {
        this.transactionCommitStrategy = commitStrategy;
    }

    public TimedTaskManager getTimedTaskManager() {
        return timedTaskManager;
    }

    public void setTimedTaskManager(TimedTaskManager timedTaskManager) {
        this.timedTaskManager = timedTaskManager;
    }
}
