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

import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO Add Class documentation for ConnectionFactoryResourceTest
 */
public class SessionPoolTest {
    private ActiveMQConnectionFactory connectionFactory;

    @Before
    public void setup() {
        connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false");
    }

    @After
    public void teardown() {
        connectionFactory = null;
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.SessionPoolTest#createObject()}
     * 
     * @throws Exception
     */
    @Test
    public void testCreateObject() throws Exception {
        ConnectionFactoryResource connections = new ConnectionFactoryResource(1, connectionFactory);
        connections.fillPool();
        SessionPool sessions = new SessionPool(1, connections);
        sessions.fillPool();
        assertNotNull(sessions);
        Session session = sessions.createObject();
        assertNotNull(session);
        sessions.drainPool();
        connections.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#borrowObject()}.
     * 
     * @throws Exception
     */
    @Test
    public void testBorrowObject() throws Exception {
        ConnectionFactoryResource connections = new ConnectionFactoryResource(1, connectionFactory);
        connections.fillPool();
        SessionPool sessions = new SessionPool(1, connections);
        sessions.fillPool();
        assertNotNull(sessions);
        ActiveMQSession session = (ActiveMQSession)sessions.borrowObject();
        assertNotNull(session);
        assertTrue(!session.isClosed());

        ActiveMQSession session2 = (ActiveMQSession)sessions.borrowObject();
        assertNull(session2);
        sessions.drainPool();
        connections.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#returnObject(java.lang.Object)}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testReturnObject() throws Exception {
        ConnectionFactoryResource connections = new ConnectionFactoryResource(1, connectionFactory);
        connections.fillPool();
        SessionPool sessions = new SessionPool(1, connections);
        sessions.fillPool();
        assertNotNull(sessions);
        ActiveMQSession session = (ActiveMQSession)sessions.borrowObject();
        assertNotNull(session);
        assertTrue(!session.isClosed());

        ActiveMQSession session2 = (ActiveMQSession)sessions.borrowObject();
        assertNull(session2);

        sessions.returnObject(session);
        session2 = (ActiveMQSession)sessions.borrowObject();
        assertNotNull(session2);

        sessions.drainPool();
        connections.drainPool();
    }

}
