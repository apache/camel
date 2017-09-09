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

import javax.annotation.Resource;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.boot.CamelNonInvasiveCamelContextTest.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = { TestApplication.class, CamelNonInvasiveCamelContextTest.class })
public class CamelNonInvasiveCamelContextTest {

    // Collaborators fixtures

    @Autowired
    CamelContext camelContext;

    @Autowired
    ProducerTemplate producerTemplate;

    @Resource(name = "xmlProducerTemplate")
    ProducerTemplate xmlProducerTemplate;

    @Autowired
    ConsumerTemplate consumerTemplate;

    @Resource(name = "xmlConsumerTemplate")
    ConsumerTemplate xmlConsumerTemplate;

    // Tests

    @Test
    public void shouldUseCamelContextFromXml() {
        assertNotNull(camelContext);
        assertEquals("xmlCamelContext", camelContext.getName());
    }

    @Test
    public void shouldUseProducerTemplateFromXml() {
        assertNotNull(producerTemplate);
        assertEquals(xmlProducerTemplate, producerTemplate);
    }

    @Test
    public void shouldUseConsumerTemplateFromXml() {
        assertNotNull(consumerTemplate);
        assertEquals(xmlConsumerTemplate, consumerTemplate);
    }

    @ImportResource(value = { "classpath:externalCamelContext.xml" })
    public static class TestApplication {

    }

}