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
package org.apache.camel.component.jms;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.ParameterConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;


/**
 * Lets test the use of the ComponentConfiguration on the JMS endpoint
 */
public class JmsComponentConfigurationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsComponentConfigurationTest.class);

    protected String componentName = "activemq456";
    protected boolean verbose;

    @Test
    public void testConfiguration() throws Exception {
        Component component = context().getComponent(componentName);
        ComponentConfiguration configuration = component.createComponentConfiguration();
        SortedMap<String, ParameterConfiguration> parameterConfigurationMap = configuration.getParameterConfigurationMap();
        if (verbose) {
            Set<Map.Entry<String, ParameterConfiguration>> entries = parameterConfigurationMap.entrySet();
            for (Map.Entry<String, ParameterConfiguration> entry : entries) {
                String name = entry.getKey();
                ParameterConfiguration config = entry.getValue();
                LOG.info("Has name: {} with type {}", name, config.getParameterType().getName());
            }
        }

        assertParameterConfig(configuration, "concurrentConsumers", int.class);
        assertParameterConfig(configuration, "clientId", String.class);
        assertParameterConfig(configuration, "disableReplyTo", boolean.class);
        assertParameterConfig(configuration, "timeToLive", long.class);

        configuration.setParameter("concurrentConsumers", 10);
        configuration.setParameter("clientId", "foo");
        configuration.setParameter("disableReplyTo", true);
        configuration.setParameter("timeToLive", 1000L);

        JmsEndpoint endpoint = assertIsInstanceOf(JmsEndpoint.class, configuration.createEndpoint());
        assertEquals("endpoint.concurrentConsumers", 10, endpoint.getConcurrentConsumers());
        assertEquals("endpoint.clientId", "foo", endpoint.getClientId());
        assertEquals("endpoint.disableReplyTo", true, endpoint.isDisableReplyTo());
        assertEquals("endpoint.timeToLive", 1000L, endpoint.getTimeToLive());
    }

    public static void assertParameterConfig(ComponentConfiguration configuration, String name,
                                       Class<?> parameterType) {
        ParameterConfiguration config = configuration.getParameterConfiguration(name);
        assertNotNull("ParameterConfiguration should exist for parameter name " + name, config);
        assertEquals("ParameterConfiguration." + name + ".getName()", name, config.getName());
        assertEquals("ParameterConfiguration." + name + ".getParameterType()", parameterType,
                config.getParameterType());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent comp = jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent(componentName, comp);
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
            }
        };
    }
}
