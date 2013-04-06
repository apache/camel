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
import org.junit.After;
import org.junit.Test;

/**
 * Tests against a "remote" JMX server. Creates an RMI Registry on or near port 39000
 * and registers the simple mbean
 * <p/>
 * Only test here is the notification test since everything should work the
 * same as the platform server. May want to refactor the existing tests to
 * run the full suite on the local platform and this "remote" setup.
 */
public class JMXRemoteTest extends SimpleBeanFixture {

    JMXServiceURL url;
    JMXConnectorServer connector;
    Registry registry;

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        connector.stop();
    }

    @Override
    protected void initServer() throws Exception {
        int port = AvailablePortFinder.getNextAvailable(39000);
        registry = LocateRegistry.createRegistry(port);

        url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/" + DOMAIN);
        // create MBean server
        server = MBeanServerFactory.createMBeanServer(DOMAIN);
        // create JMXConnectorServer MBean
        connector = JMXConnectorServerFactory.newJMXConnectorServer(url, Collections.<String, Object>emptyMap(), server);
        connector.start();
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        String uri = url.toString();
        return super.buildFromURI().withServerName(uri);
    }

    @Test
    public void notification() throws Exception {
        getSimpleMXBean().touch();
        getMockFixture().waitForMessages();
        getMockFixture().assertMessageReceived(new File("src/test/resources/consumer-test/touched.xml"));
    }
}
