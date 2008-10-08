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

import java.lang.management.ManagementFactory;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.springframework.context.support.AbstractXmlApplicationContext;
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

    @SuppressWarnings("unchecked")
    protected void releaseMBeanServers() {
        List<MBeanServer> servers =
            (List<MBeanServer>)MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    public void testQueryMbeans() throws Exception {
        assertEquals(1, mbsc.queryNames(new ObjectName("org.apache.camel" + ":type=routes,*"), null).size());
        assertEquals(1, mbsc.queryNames(new ObjectName("org.apache.camel" + ":type=processors,*"), null).size());
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/defaultJmxConfig.xml");
    }

    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            mbsc = ManagementFactory.getPlatformMBeanServer();
        }
        return mbsc;
    }
    
}
