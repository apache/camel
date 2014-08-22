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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO Add Class documentation for ConnectionFactoryResourceTest
 */
public class ConnectionFactoryResourceTest {
    private ActiveMQConnectionFactory connectionFactory;

    @Before
    public void setup() {
        connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");
    }

    @After
    public void teardown() {
        connectionFactory = null;
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource#makeObject()}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testCreateObject() throws Exception {
        ConnectionFactoryResource pool = new ConnectionFactoryResource(1, connectionFactory);
        pool.fillPool();
        assertNotNull(pool);
        ActiveMQConnection connection = (ActiveMQConnection)pool.makeObject();
        assertNotNull(connection);
        assertTrue(connection.isStarted());
        pool.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource#destroyObject()}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testDestroyObject() throws Exception {
        ConnectionFactoryResource pool = new ConnectionFactoryResource(1, connectionFactory);
        pool.fillPool();
        assertNotNull(pool);
        ActiveMQConnection connection = (ActiveMQConnection)pool.makeObject();
        assertNotNull(connection);
        assertTrue(connection.isStarted());
        pool.drainPool();
        assertTrue(pool.size() == 0);
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ConnectionResource#borrowConnection()}.
     * 
     * @throws Exception
     */
    @Test(expected=NoSuchElementException.class)
    public void testBorrowObject() throws Exception {
        ConnectionFactoryResource pool = new ConnectionFactoryResource(1, connectionFactory);
        pool.fillPool();
        assertNotNull(pool);
        ActiveMQConnection connection = (ActiveMQConnection)pool.borrowConnection();
        assertNotNull(connection);
        assertTrue(connection.isStarted());
        pool.borrowConnection();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ConnectionResource#returnConnection(java.lang.Object)}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testReturnObject() throws Exception {
        ConnectionFactoryResource pool = new ConnectionFactoryResource(1, connectionFactory);
        pool.fillPool();
        assertNotNull(pool);
        ActiveMQConnection connection = (ActiveMQConnection)pool.borrowConnection();
        assertNotNull(connection);
        assertTrue(connection.isStarted());
        pool.returnConnection(connection);
        ActiveMQConnection connection2 = (ActiveMQConnection)pool.borrowConnection();
        assertNotNull(connection2);
        pool.drainPool();
    }

}
