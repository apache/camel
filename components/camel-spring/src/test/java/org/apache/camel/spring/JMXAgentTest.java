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

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test that verifies JMX properties can be configured via
 * Spring.
 *
 * @version $Revision$
 *
 */
public class JMXAgentTest extends DefaultJMXAgentTest {

    protected static final String JMXSERVICEURL =
        "service:jmx:rmi:///jndi/rmi://localhost:20008/jmxrmi/camel";
    protected JMXConnector clientConnector;

    @Override
    protected void setUp() throws Exception {
        sleepForConnection = 2000;
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {

        if (clientConnector != null) {
            try {
                clientConnector.close();
            } catch (Exception e) {
                // ignore
            }
            clientConnector = null;
        }
        super.tearDown();
    }

    @Override
    protected String getDomainName() {
        return "org.apache.camel.test";
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/jmxConfig.xml");
    }

    @Override
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            if (clientConnector == null) {
                clientConnector = JMXConnectorFactory.connect(
                        new JMXServiceURL(JMXSERVICEURL), null);
            }
            mbsc = clientConnector.getMBeanServerConnection();
        }
        return mbsc;
    }

}
