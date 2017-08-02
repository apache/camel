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
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.awaitility.Awaitility.await;

/**
 * Test that verifies JMX is enabled by default.
 *
 * @version 
 */
public class DefaultJMXAgentTest extends SpringTestSupport {

    protected MBeanServerConnection mbsc;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected void setUp() throws Exception {
        releaseMBeanServers();
        super.setUp();

        await().atMost(3, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
            mbsc = getMBeanConnection();
            return true;
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            releaseMBeanServers();
        } finally {
            mbsc = null;
            super.tearDown();
        }
    }

    protected void releaseMBeanServers() {
        List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    public void testQueryMbeans() throws Exception {
        // whats the numbers before, because the JVM can have left overs when unit testing
        int before = mbsc.queryNames(new ObjectName("org.apache.camel" + ":type=consumers,*"), null).size();

        // start route should enlist the consumer to JMX
        context.startRoute("foo");

        int after = mbsc.queryNames(new ObjectName("org.apache.camel" + ":type=consumers,*"), null).size();

        assertTrue("Should have added consumer to JMX, before: " + before + ", after: " + after, after > before);
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
