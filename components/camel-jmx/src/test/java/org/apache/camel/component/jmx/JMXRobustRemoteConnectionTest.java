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
package org.apache.camel.component.jmx;

import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;

import javax.management.MBeanServerFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test to verify:
 * 
 * 1.  The JMX consumer can actively connect (via polling) to a JMX server that is not listening 
 *     for connections when the route is started
 *     
 * 2.  The JMX consumer can detect a lost JMX connection, and will reconnect to the JMX server
 *     when the server is listening for connections again on the configured port
 */
public class JMXRobustRemoteConnectionTest extends SimpleBeanFixture {

    JMXServiceURL url;
    JMXConnectorServer connector;
    Registry registry;
    int port;
    
    @BeforeEach
    @Override
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/" + DOMAIN);

        initContext();
        startContext();
    }
    
    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        connector.stop();
    }

    @Override
    protected void initServer() throws Exception {
        if (registry == null) {
            registry = LocateRegistry.createRegistry(port);
        }    
        // create MBean server
        server = MBeanServerFactory.createMBeanServer(DOMAIN);
        // create JMXConnectorServer MBean
        connector = JMXConnectorServerFactory.newJMXConnectorServer(url, Collections.<String, Object>emptyMap(), server);
        connector.start();
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        String uri = url.toString();
        return super.buildFromURI().withServerName(uri).withTestConnectionOnStartup(false).withReconnectDelay(1).withReconnectOnConnectionFailure(true);
    }

    @Test
    public void testRobustConnection() throws Exception {
        
        // the JMX service should not be started
        try {
            getSimpleMXBean().touch();
            fail("The mxbean should not be available.");
        } catch (Exception e) {
            assertTrue(e instanceof java.lang.IllegalArgumentException);
            assertTrue(e.getMessage().equals("Null connection"));
        }
        
        // start the server;  the JMX consumer should connect and start;  the mock should receive a notification
        initServer();
        initBean();
        Thread.sleep(2000);
        getSimpleMXBean().touch();
        getMockFixture().waitForMessages();
        getMockFixture().assertMessageReceived(new File("src/test/resources/consumer-test/touched.xml"));

        // stop the server; the JMX consumer should lose connectivity and the mock will not receive notifications
        connector.stop();
        Thread.sleep(2000);
        getMockFixture().resetMockEndpoint();
        getMockFixture().getMockEndpoint().setExpectedMessageCount(1);
        getSimpleMXBean().touch();
        getMockFixture().getMockEndpoint().assertIsNotSatisfied();

        // restart the server;  the JMX consumer should re-connect and the mock should receive a notification
        initServer();
        initBean();
        Thread.sleep(2000);
        getSimpleMXBean().touch();
        getMockFixture().waitForMessages();
        getMockFixture().assertMessageReceived(new File("src/test/resources/consumer-test/touched.xml"));
    }
    
}
