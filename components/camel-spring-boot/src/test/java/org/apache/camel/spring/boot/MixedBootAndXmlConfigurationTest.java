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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    properties = {
        "camel.springboot.name = camel-spring-boot",
        "camel.springboot.shutdownTimeout = 5"
    }
)
public class MixedBootAndXmlConfigurationTest {

    @Autowired
    private CamelContext camel;

    @Test
    public void thereShouldBeAutoConfiguredFromSpringBoot() {
        Assert.assertEquals("camel-spring-boot", camel.getName());
        Assert.assertEquals(5, camel.getShutdownStrategy().getTimeout());
    }

    @Configuration
    @EnableAutoConfiguration
    @ImportResource("classpath:mixed-camel-context.xml")
    public static class TestConfiguration {
    }
}
