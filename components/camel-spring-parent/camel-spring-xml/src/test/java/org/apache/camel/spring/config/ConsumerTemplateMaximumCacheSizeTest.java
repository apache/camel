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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration
public class ConsumerTemplateMaximumCacheSizeTest extends SpringRunWithTestSupport {

    @Autowired
    private ConsumerTemplate template;

    @Autowired
    private CamelContext context;

    @Test
    public void testTemplateMaximumCache() throws Exception {
        assertNotNull(template, "Should have injected a consumer template");

        ConsumerTemplate lookup = context.getRegistry().lookupByNameAndType("template", ConsumerTemplate.class);
        assertNotNull(lookup, "Should lookup consumer template");

        assertEquals(50, template.getMaximumCacheSize());
    }

}
