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
package org.apache.camel.spring.config;

import javax.annotation.Resource;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class ProducerTemplateMixedAutoRegisterTwoCamelContextsTest extends SpringRunWithTestSupport {

    @Resource(name = "camel1")
    private CamelContext context1;

    @Resource(name = "camel2")
    private CamelContext context2;

    @Test
    public void testHasTemplateCamel1() {
        DefaultProducerTemplate lookup = context1.getRegistry().lookupByNameAndType("template1", DefaultProducerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);
        assertEquals("camel1", lookup.getCamelContext().getName());
    }

    @Test
    public void testHasTemplateCamel2() {
        DefaultProducerTemplate lookup = context1.getRegistry().lookupByNameAndType("template2", DefaultProducerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);
        assertEquals("camel2", lookup.getCamelContext().getName());
    }

    @Test
    public void testHasNoConsumerTemplateCamel1() {
        ConsumerTemplate lookup = context1.getRegistry().lookupByNameAndType("consumerTemplate", ConsumerTemplate.class);
        assertNull("Should NOT lookup consumer template", lookup);
    }

    @Test
    public void testHasNoConsumerTemplateCamel2() {
        ConsumerTemplate lookup = context2.getRegistry().lookupByNameAndType("consumerTemplate", ConsumerTemplate.class);
        assertNull("Should NOT lookup consumer template", lookup);
    }
}
