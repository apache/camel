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
package org.apache.camel.component.hystrix.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.HystrixDefinition;
import org.apache.camel.model.RouteDefinition;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Testing the Hystrix configuration hierarchy
 */
@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = HystrixHierarchicalConfiguration.class)
@SpringBootTest(properties = {
    "debug=false",
    "camel.hystrix.enabled=true",
    "camel.hystrix.group-key=global-group-key",
    "camel.hystrix.thread-pool-key=global-thread-key",
    "camel.hystrix.core-pool-size=10"
})
public class HystrixHierarchicalConfigurationTest {
    @Autowired
    CamelContext camelContext;

    @Test
    public void testConfiguration() throws Exception {
        RouteDefinition routeDefinition = camelContext.getRouteDefinition("hystrix-route");
        HystrixDefinition hystrixDefinition = findHystrixDefinition(routeDefinition);

        Assert.assertNotNull(hystrixDefinition);

        HystrixProcessorFactory factory = new HystrixProcessorFactory();
        HystrixConfigurationDefinition config = factory.buildHystrixConfiguration(camelContext, hystrixDefinition);

        Assert.assertEquals("local-conf-group-key", config.getGroupKey());
        Assert.assertEquals("global-thread-key", config.getThreadPoolKey());
        Assert.assertEquals(new Integer(5), config.getCorePoolSize());
    }

    // **********************************************
    // Helper
    // **********************************************

    private HystrixDefinition findHystrixDefinition(RouteDefinition routeDefinition) throws Exception {
        return routeDefinition.getOutputs().stream()
            .filter(HystrixDefinition.class::isInstance)
            .map(HystrixDefinition.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find a HystrixDefinition"));
    }
}

