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
package org.apache.camel.spring;

import java.util.Map;

import org.apache.camel.impl.engine.DefaultConsumerTemplate;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CamelContextAwareTest extends SpringTestSupport {
    protected CamelContextAwareBean bean1;

    @Test
    public void testInjectionPoints() throws Exception {
        assertNotNull(bean1.getCamelContext(), "No CamelContext injected!");
        Map<String, String> globalOptions = bean1.getCamelContext().getGlobalOptions();
        assertNotNull(globalOptions, "The global options reference should not be null");
        assertEquals(1, globalOptions.size(), "No global options injected");
        assertEquals("this is a test first", globalOptions.get("org.apache.camel.test"),
                "Should get the value of org.apache.camel.test");
    }

    @Test
    public void testCamelTemplates() throws Exception {
        DefaultProducerTemplate producer1 = getMandatoryBean(DefaultProducerTemplate.class, "producer1");
        assertEquals("camel1", producer1.getCamelContext().getName(), "Inject a wrong camel context");

        DefaultProducerTemplate producer2 = getMandatoryBean(DefaultProducerTemplate.class, "producer2");
        assertEquals("camel2", producer2.getCamelContext().getName(), "Inject a wrong camel context");

        DefaultConsumerTemplate consumer = getMandatoryBean(DefaultConsumerTemplate.class, "consumer");
        assertEquals("camel2", consumer.getCamelContext().getName(), "Inject a wrong camel context");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        bean1 = getMandatoryBean(CamelContextAwareBean.class, "bean1");
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextAwareBean.xml");
    }

}
