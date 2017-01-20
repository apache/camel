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

import org.apache.camel.cloud.LoadBalancer;
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelCloudAutoConfiguration.class,
        ServiceCallServiceChooserAutoConfiguration.class
    },
    properties = {
        "camel.cloud.servicecall.enabled=false",
        "camel.cloud.servicecall.service-discovery.enabled=false",
        "camel.cloud.servicecall.service-filter.enabled=false",
        "camel.cloud.servicecall.service-chooser.enabled=true",
        "camel.cloud.servicecall.load-balancer.enabled=false",
        "debug=false"
    }
)
public class CamelCloudServiceCallConfigurationTest {
    @Autowired
    private ApplicationContext ctx;

    @Test
    public void doTest() throws Exception {
        Environment env = ctx.getEnvironment();

        assertFalse(env.getProperty("camel.cloud.servicecall.enabled", Boolean.class));
        assertFalse(env.getProperty("camel.cloud.servicecall.service-discovery.enabled", Boolean.class));
        assertFalse(env.getProperty("camel.cloud.servicecall.service-filter.enabled", Boolean.class));
        assertTrue(env.getProperty("camel.cloud.servicecall.service-chooser.enabled", Boolean.class));
        assertFalse(env.getProperty("camel.cloud.servicecall.load-balancer.enabled", Boolean.class));

        assertTrue(ctx.getBeansOfType(ServiceDiscovery.class).isEmpty());
        assertTrue(ctx.getBeansOfType(ServiceFilter.class).isEmpty());
        assertFalse(ctx.getBeansOfType(ServiceChooser.class).isEmpty());
        assertTrue(ctx.getBeansOfType(LoadBalancer.class).isEmpty());
    }
}

