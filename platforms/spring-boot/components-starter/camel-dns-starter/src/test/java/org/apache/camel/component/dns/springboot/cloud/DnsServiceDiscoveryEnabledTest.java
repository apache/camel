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
package org.apache.camel.component.dns.springboot.cloud;

import java.util.Map;

import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.model.cloud.springboot.DnsServiceCallServiceDiscoveryConfigurationProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        DnsServiceDiscoveryEnabledTest.TestConfiguration.class
    },
    properties = {
        "debug=false",
        "camel.cloud.dns.service-discovery.enabled=true"
})
public class DnsServiceDiscoveryEnabledTest {
    @Autowired
    ApplicationContext context;

    @Test
    public void testConfiguration() throws Exception {
        Map<String, ?> beans;

        beans = context.getBeansOfType(DnsServiceCallServiceDiscoveryConfigurationProperties.class);
        Assert.assertFalse(beans.isEmpty());
        Assert.assertEquals(1, beans.size());

        beans = context.getBeansOfType(ServiceDiscovery.class);
        Assert.assertFalse(beans.isEmpty());
        Assert.assertTrue(beans.containsKey("dns-service-discovery"));
    }

    @Configuration
    public static class TestConfiguration {
    }
}
