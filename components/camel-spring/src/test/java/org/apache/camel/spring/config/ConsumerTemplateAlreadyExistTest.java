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
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class ConsumerTemplateAlreadyExistTest extends SpringRunWithTestSupport {

    @Autowired
    private ConsumerTemplate template;

    @Autowired
    private CamelContext context;

    @Test
    public void testHasExistingTemplate() {
        assertNotNull("Should have injected a consumer template", template);

        ConsumerTemplate lookup = context.getRegistry().lookupByNameAndType("myConsumerTemplate", ConsumerTemplate.class);
        assertNotNull("Should lookup consumer template", lookup);

        ConsumerTemplate lookup2 = context.getRegistry().lookupByNameAndType("consumerTemplate", ConsumerTemplate.class);
        assertNull("Should not be able to lookup consumer template", lookup2);
    }

    @Test
    public void testShouldBeSingleton() {
        ConsumerTemplate lookup = context.getRegistry().lookupByNameAndType("myConsumerTemplate", ConsumerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);

        ConsumerTemplate lookup2 = context.getRegistry().lookupByNameAndType("myConsumerTemplate", ConsumerTemplate.class);
        assertNotNull("Should lookup producer template", lookup);

        assertSame("Should be same instances (singleton)", lookup, lookup2);
    }

}