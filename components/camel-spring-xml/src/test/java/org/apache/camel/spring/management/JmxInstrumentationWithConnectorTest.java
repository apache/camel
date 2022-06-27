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
package org.apache.camel.spring.management;

import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spring.EndpointReferenceTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs(OS.AIX)
public class JmxInstrumentationWithConnectorTest extends EndpointReferenceTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testJmxConfiguration() throws Exception {
        ManagementAgent agent = getMandatoryBean(DefaultManagementAgent.class, "agent");
        assertNotNull(agent, "SpringInstrumentationAgent must be configured for JMX support");
        assertNotNull(agent.getMBeanServer(), "MBeanServer must be configured for JMX support");
        assertEquals("org.apache.camel.test", agent.getMBeanServer().getDefaultDomain());
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/jmxInstrumentationWithConnector.xml");
    }

    @Override
    @Disabled("This test is not applicable in this scenario")
    public void testReferenceEndpointFromOtherCamelContext() {
        // don't run the test in this method
    }
}
