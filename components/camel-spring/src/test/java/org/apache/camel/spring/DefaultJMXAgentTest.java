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
package org.apache.camel.spring;

import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

import org.apache.camel.management.DefaultInstrumentationAgent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test that verifies JMX is enabled by default.
 *
 * @version $Revision$
 *
 */
public class DefaultJMXAgentTest extends SpringTestSupport {

    protected MBeanServerConnection mbsc;
    protected long sleepForConnection;

    @Override
    protected void setUp() throws Exception {
        releaseMBeanServers();
        super.setUp();
        Thread.sleep(sleepForConnection);
        mbsc = getMBeanConnection();
    }

    @Override
    protected void tearDown() throws Exception {
        releaseMBeanServers();
        mbsc = null;
        super.tearDown();
    }

    protected String getDomainName() {
        return DefaultInstrumentationAgent.DEFAULT_DOMAIN;
    }

    @SuppressWarnings("unchecked")
    protected void releaseMBeanServers() {
        List<MBeanServer> servers =
            (List<MBeanServer>)MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    public void testGetJMXConnector() throws Exception {
        assertEquals("Get the wrong domain name", mbsc.getDefaultDomain(), getDomainName());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/defaultJmxConfig.xml");
    }

    @SuppressWarnings("unchecked")
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            List<MBeanServer> servers =
                    (List<MBeanServer>)MBeanServerFactory.findMBeanServer(null);

            for (MBeanServer server : servers) {
                if (getDomainName().equals(server.getDefaultDomain())) {
                    mbsc = server;
                    break;
                }
            }
        }
        return mbsc;
    }
}
