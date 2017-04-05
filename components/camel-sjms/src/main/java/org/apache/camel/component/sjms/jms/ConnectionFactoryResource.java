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
package org.apache.camel.component.sjms.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Session;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * The default {@link ConnectionResource} implementation for the SJMSComponent.
 */
public class ConnectionFactoryResource extends BasePoolableObjectFactory<Connection> implements ConnectionResource {

    private static final long DEFAULT_WAIT_TIMEOUT = 5 * 1000;
    private static final int DEFAULT_POOL_SIZE = 1;
    private GenericObjectPool<Connection> connections;
    private ConnectionFactory connectionFactory;
    private String username;
    private String password;
    private String clientId;
    private ExceptionListener exceptionListener;

    /**
     * Default Constructor
     */
    public ConnectionFactoryResource() {
        this(DEFAULT_POOL_SIZE, null);
    }

    public ConnectionFactoryResource(int poolSize, ConnectionFactory connectionFactory) {
        this(poolSize, connectionFactory, null, null);
    }

    public ConnectionFactoryResource(int poolSize, ConnectionFactory connectionFactory, String username, String password) {
        this(poolSize, connectionFactory, username, password, null);
    }

    public ConnectionFactoryResource(int poolSize, ConnectionFactory connectionFactory, String username, String password, String connectionId) {
        this(poolSize, connectionFactory, username, password, connectionId, DEFAULT_WAIT_TIMEOUT);
    }

    public ConnectionFactoryResource(int poolSize, ConnectionFactory connectionFactory, String username, String password, String connectionId, long maxWait) {
        this(poolSize, connectionFactory, username, password, connectionId, DEFAULT_WAIT_TIMEOUT, true);
    }

    public ConnectionFactoryResource(int poolSize, ConnectionFactory connectionFactory, String username, String password, String connectionId,
                                     long maxWait, boolean testOnBorrow) {
        this.connectionFactory = connectionFactory;
        this.username = username;
        this.password = password;
        this.clientId = connectionId;
        this.connections = new GenericObjectPool<Connection>(this);
        this.connections.setMaxWait(maxWait);
        this.connections.setMaxActive(poolSize);
        this.connections.setMaxIdle(poolSize);
        this.connections.setMinIdle(poolSize);
        this.connections.setLifo(false);
        this.connections.setTestOnBorrow(testOnBorrow);
    }

    @Override
    public boolean validateObject(Connection connection) {
        try {
            // ensure connection works so we need to start it
            connection.start();
            return true;
        } catch (Throwable e) {
            // ignore
        }

        return false;
    }

    @Override
    public Connection borrowConnection() throws Exception {
        return connections.borrowObject();
    }

    @Override
    public void returnConnection(Connection connection) throws Exception {
        connections.returnObject(connection);
    }

    @Override
    public Connection makeObject() throws Exception {
        Connection connection = null;
        if (connectionFactory != null) {
            if (getUsername() != null && getPassword() != null) {
                connection = connectionFactory.createConnection(getUsername(), getPassword());
            } else {
                connection = connectionFactory.createConnection();
            }
        }
        if (connection != null) {
            if (ObjectHelper.isNotEmpty(getClientId())) {
                connection.setClientID(getClientId());
            }
            // we want to listen for exceptions
            if (exceptionListener != null) {
                connection.setExceptionListener(exceptionListener);
            }
            connection.start();
        }
        return connection;
    }

    @Override
    public void destroyObject(Connection connection) throws Exception {
        if (connection != null) {
            connection.stop();
            connection.close();
        }
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public int size() {
        return connections.getNumActive() + connections.getNumIdle();
    }

    public void fillPool() throws Exception {
        while (connections.getNumIdle() < connections.getMaxIdle()) {
            connections.addObject();
        }
    }

    public void drainPool() throws Exception {
        connections.close();
    }
}
