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

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test that verifies JMX properties can be configured via Spring.
 *
 * @version 
 */
public class JMXAgentTest extends DefaultJMXAgentTest {

    protected final String jmxServiceUrl = "service:jmx:rmi:///jndi/rmi://localhost:" + getPort() + "/jmxrmi/camel";
    protected JMXConnector clientConnector;

    protected int getPort() {
        return 20008;
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
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/jmxConfig.xml");
    }

    @Override
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            if (clientConnector == null) {
                clientConnector = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl), null);
            }
            mbsc = clientConnector.getMBeanServerConnection();
        }
        return mbsc;
    }

}
