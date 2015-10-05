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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ManagementAgent;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test that verifies JMX properties can be configured via Spring.
 */
public class JMXAgentPropertiesTest extends JMXAgentTest {

    @Override
    protected int getPort() {
        return 20009;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/jmxConfigUsingProperties.xml");
    }

    public void testEnableUseHostIPAddress() throws Exception {
        CamelContext ctx = createCamelContext();
        ManagementAgent agent = ctx.getManagementStrategy().getManagementAgent();
        agent.start();
        assertTrue(agent.getUseHostIPAddress());
    }

}