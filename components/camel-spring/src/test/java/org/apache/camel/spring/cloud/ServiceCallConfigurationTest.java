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
package org.apache.camel.spring.cloud;

import org.apache.camel.impl.cloud.ServiceCallConstants;
import org.apache.camel.model.cloud.AggregatingServiceCallServiceDiscoveryConfiguration;
import org.apache.camel.model.cloud.BlacklistServiceCallServiceFilterConfiguration;
import org.apache.camel.model.cloud.ChainedServiceCallServiceFilterConfiguration;
import org.apache.camel.model.cloud.DefaultServiceCallServiceLoadBalancerConfiguration;
import org.apache.camel.model.cloud.HealthyServiceCallServiceFilterConfiguration;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.cloud.ServiceCallExpressionConfiguration;
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServiceCallConfigurationTest {
    @Test
    public void testServiceDiscoveryConfiguration() {
        SpringCamelContext context = createContext("org/apache/camel/spring/cloud/ServiceCallConfigurationTest.xml");

        testConfiguration1(context.getServiceCallConfiguration("conf1"));
        testConfiguration2(context.getServiceCallConfiguration("conf2"));
    }

    protected void testConfiguration1(ServiceCallConfigurationDefinition conf) {
        assertNotNull("conf1", conf);

        assertNotNull("No ServiceCallConfiguration (1)", conf);
        assertNotNull("No ServiceDiscoveryConfiguration (1)", conf.getServiceDiscoveryConfiguration());
        assertNotNull("No ServiceCallLoadBalancerConfiguration (1)", conf.getLoadBalancerConfiguration());
        assertTrue(conf.getLoadBalancerConfiguration() instanceof DefaultServiceCallServiceLoadBalancerConfiguration);

        ServiceCallExpressionConfiguration expConf1 = conf.getExpressionConfiguration();
        assertNull(expConf1.getExpression());
        assertNotNull("No ServiceCallExpressionConfiguration (1)", expConf1.getExpressionType());
        assertEquals(ServiceCallConstants.SERVICE_HOST, expConf1.getHostHeader());
        assertEquals(ServiceCallConstants.SERVICE_PORT, expConf1.getPortHeader());

        StaticServiceCallServiceDiscoveryConfiguration discovery1 = (StaticServiceCallServiceDiscoveryConfiguration)conf.getServiceDiscoveryConfiguration();
        assertEquals(1, discovery1.getServers().size());
        assertEquals("localhost:9091", discovery1.getServers().get(0));
    }

    protected void testConfiguration2(ServiceCallConfigurationDefinition conf) {
        assertNotNull("conf2", conf);

        assertNotNull("No ServiceCallConfiguration (2)", conf);
        assertNotNull("No ServiceDiscoveryConfiguration (2)", conf.getServiceDiscoveryConfiguration());
        assertNull(conf.getLoadBalancerConfiguration());

        AggregatingServiceCallServiceDiscoveryConfiguration discovery2 = (AggregatingServiceCallServiceDiscoveryConfiguration)conf.getServiceDiscoveryConfiguration();
        assertEquals(2, discovery2.getServiceDiscoveryConfigurations().size());
        assertTrue(discovery2.getServiceDiscoveryConfigurations().get(0) instanceof StaticServiceCallServiceDiscoveryConfiguration);
        assertTrue(discovery2.getServiceDiscoveryConfigurations().get(1) instanceof StaticServiceCallServiceDiscoveryConfiguration);

        ServiceCallExpressionConfiguration expconf = conf.getExpressionConfiguration();
        assertNull(expconf.getExpression());
        assertNull(expconf.getExpressionType());
        assertEquals("MyHostHeader", expconf.getHostHeader());
        assertEquals("MyPortHeader", expconf.getPortHeader());

        StaticServiceCallServiceDiscoveryConfiguration sconf1 = (StaticServiceCallServiceDiscoveryConfiguration)discovery2.getServiceDiscoveryConfigurations().get(0);
        assertEquals(1, sconf1.getServers().size());
        assertEquals("localhost:9092", sconf1.getServers().get(0));

        StaticServiceCallServiceDiscoveryConfiguration sconf = (StaticServiceCallServiceDiscoveryConfiguration)discovery2.getServiceDiscoveryConfigurations().get(1);
        assertEquals(1, sconf.getServers().size());
        assertEquals("localhost:9093,localhost:9094,localhost:9095,localhost:9096", sconf.getServers().get(0));

        ChainedServiceCallServiceFilterConfiguration filter = (ChainedServiceCallServiceFilterConfiguration)conf.getServiceFilterConfiguration();
        assertEquals(2, filter.getServiceFilterConfigurations().size());
        assertTrue(filter.getServiceFilterConfigurations().get(0) instanceof HealthyServiceCallServiceFilterConfiguration);
        assertTrue(filter.getServiceFilterConfigurations().get(1) instanceof BlacklistServiceCallServiceFilterConfiguration);
    }

    protected SpringCamelContext createContext(String classpathConfigFile) {
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext(classpathConfigFile);

        SpringCamelContext camelContext = appContext.getBean(SpringCamelContext.class);
        assertNotNull("No Camel Context in file: " + classpathConfigFile, camelContext);

        return camelContext;
    }
}
