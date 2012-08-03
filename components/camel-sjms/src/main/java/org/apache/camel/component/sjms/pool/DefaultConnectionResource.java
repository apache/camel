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
package org.apache.camel.component.sjms.pool;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;

import org.apache.camel.component.sjms.ConnectionResource;
import org.apache.camel.util.ObjectHelper;

/**
 * TODO Add Class documentation for DefaultConnectionResource
 * 
 */
public class DefaultConnectionResource extends ObjectPool<Connection> implements ConnectionResource {
    private ConnectionFactory connectionFactory;
    private String username;
    private String password;
    private String connectionId;

    /**
     * TODO Add Constructor Javadoc
     *
     */
    public DefaultConnectionResource() {
        super();
    }

    /**
     * TODO Add Constructor Javadoc
     * 
     * @param poolSize
     * @param connectionFactory
     */
    public DefaultConnectionResource(int poolSize, ConnectionFactory connectionFactory) {
        this(poolSize, connectionFactory, null, null);
    }

    /**
     * TODO Add Constructor Javadoc
     * 
     * @param poolSize
     * @param connectionFactory
     * @param username
     * @param password
     */
    public DefaultConnectionResource(int poolSize, ConnectionFactory connectionFactory, String username, String password) {
        super(poolSize);
        this.connectionFactory = connectionFactory;
        this.username = username;
        this.password = password;
    }

    /**
     * TODO Add Constructor Javadoc
     * 
     * @param poolSize
     * @param connectionFactory
     * @param username
     * @param password
     */
    public DefaultConnectionResource(int poolSize, ConnectionFactory connectionFactory, String username, String password, String connectionId) {
        super(poolSize);
        this.connectionFactory = connectionFactory;
        this.username = username;
        this.password = password;
        this.connectionId = connectionId;
    }
    
    @Override
    public Connection borrowConnection() throws Exception {
        return this.borrowObject();
    }
    
    @Override
    public Connection borrowConnection(long timeout) throws Exception {
        return this.borrowObject(timeout);
    }
    
    @Override
    public void returnConnection(Connection connection) throws Exception {
        returnObject(connection);
    }

    @Override
    protected Connection createObject() throws Exception {
        Connection connection = null;
        if (connectionFactory != null) {
            if (getUsername() != null && getPassword() != null) {
                connection = connectionFactory.createConnection(getUsername(), getPassword());
            } else {
                connection = connectionFactory.createConnection();
            }
        }
        if (connection != null) {
            if (ObjectHelper.isNotEmpty(getConnectionId())) {
                connection.setClientID(getConnectionId());
            }
            connection.start();
        }
        return connection;
    }
    
    @Override
    protected void destroyObject(Connection connection) throws Exception {
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

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
}
