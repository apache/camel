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
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.ServiceSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@DirtiesContext
public class CamelSpringBootTemplateShutdownTest {

    CamelContext camelContext;

    AbstractApplicationContext applicationContext;

    ConsumerTemplate consumerTemplate;

    ProducerTemplate producerTemplate;

    FluentProducerTemplate fluentProducerTemplate;

    @Before
    public void setupApplicationContext() {
        applicationContext = new AnnotationConfigApplicationContext(CamelAutoConfiguration.class);
        camelContext = applicationContext.getBean(CamelContext.class);
        consumerTemplate = applicationContext.getBean(ConsumerTemplate.class);
        producerTemplate = applicationContext.getBean(ProducerTemplate.class);
        fluentProducerTemplate = applicationContext.getBean(FluentProducerTemplate.class);
    }

    @Test
    public void shouldStopTemplatesWithCamelShutdown() throws Exception {
        assertTrue(((ServiceSupport) consumerTemplate).isStarted());
        assertTrue(((ServiceSupport) producerTemplate).isStarted());
        assertTrue(((ServiceSupport) fluentProducerTemplate).isStarted());

        camelContext.stop();

        assertTrue(((ServiceSupport) camelContext).isStopped());
        assertTrue(((ServiceSupport) consumerTemplate).isStopped());
        assertTrue(((ServiceSupport) producerTemplate).isStopped());
        assertTrue(((ServiceSupport) fluentProducerTemplate).isStopped());
    }

    @Test
    public void shouldStopTemplatesWithApplicationContextShutdown() throws Exception {
        assertTrue(((ServiceSupport) consumerTemplate).isStarted());
        assertTrue(((ServiceSupport) producerTemplate).isStarted());
        assertTrue(((ServiceSupport) fluentProducerTemplate).isStarted());

        applicationContext.close();

        assertFalse(applicationContext.isActive());
        assertTrue(((ServiceSupport) camelContext).isStopped());
        assertTrue(((ServiceSupport) consumerTemplate).isStopped());
        assertTrue(((ServiceSupport) producerTemplate).isStopped());
        assertTrue(((ServiceSupport) fluentProducerTemplate).isStopped());
    }

}