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

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class ProducerTemplateAlreadyExistTest extends SpringRunWithTestSupport {

    @Autowired
    private ProducerTemplate template;

    @Autowired
    private CamelContext context;

    @Test
    public void testHasExistingTemplate() {
        assertNotNull("Should have injected a producer template", template);

        ProducerTemplate lookup = context.getRegistry().lookupByNameAndType("myTemplate", ProducerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);

        ProducerTemplate lookup2 = context.getRegistry().lookupByNameAndType("template", ProducerTemplate.class);
        assertNull("Should not be able to lookup producer template", lookup2);
    }

    @Test
    public void testShouldBeSingleton() {
        ProducerTemplate lookup = context.getRegistry().lookupByNameAndType("myTemplate", ProducerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);

        ProducerTemplate lookup2 = context.getRegistry().lookupByNameAndType("myTemplate", ProducerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);

        assertSame("Should be same instances (singleton)", lookup, lookup2);
    }
}