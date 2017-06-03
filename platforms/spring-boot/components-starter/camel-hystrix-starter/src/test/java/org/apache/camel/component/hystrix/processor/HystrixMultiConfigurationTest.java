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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.HystrixDefinition;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Testing the Hystrix multi configuration
 */
@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = HystrixMultiConfiguration.class)
@SpringBootTest(properties = {
    "debug=false",
    "camel.hystrix.enabled=true",
    "camel.hystrix.group-key=global-group",
    "camel.hystrix.configurations.conf-1.group-key=conf-1-group",
    "camel.hystrix.configurations.conf-2.group-key=conf-2-group"
})
public class HystrixMultiConfigurationTest {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private CamelContext camelContext;

    @Test
    public void testBeans() throws Exception {
        Map<String, HystrixConfigurationDefinition> beans = context.getBeansOfType(HystrixConfigurationDefinition.class);

        Assert.assertEquals(4, beans.size());
        Assert.assertEquals("global-group", beans.get(HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID).getGroupKey());
        Assert.assertEquals("bean-group", beans.get("bean-conf").getGroupKey());
        Assert.assertEquals("conf-1-group", beans.get("conf-1").getGroupKey());
        Assert.assertEquals("conf-2-group", beans.get("conf-2").getGroupKey());
    }

    @Test
    public void testConfigurations() throws Exception {
        HystrixProcessor processor1 = findHystrixProcessor(camelContext.getRoute("hystrix-route-1").navigate());
        HystrixProcessor processor2 = findHystrixProcessor(camelContext.getRoute("hystrix-route-2").navigate());

        Assert.assertEquals("conf-1-group", processor1.getHystrixGroupKey());
        Assert.assertEquals("conf-2-group", processor2.getHystrixGroupKey());
    }

    // **********************************************
    // Helper
    // **********************************************

    private HystrixProcessor findHystrixProcessor(Navigate<Processor> navigate) throws Exception {
        for (Processor processor : navigate.next()) {
            if (processor instanceof HystrixProcessor) {
                return (HystrixProcessor)processor;
            }
            if (processor instanceof Navigate) {
                return findHystrixProcessor((Navigate<Processor>) processor);
            }
        }

        throw new IllegalStateException("Unable to find an HystrixProcessor instance");
    }
}

