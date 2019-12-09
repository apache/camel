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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.awaitility.Awaitility.await;

@ContextConfiguration
public class ConsumerTemplateMaximumCacheSizeTest extends SpringRunWithTestSupport {

    @Autowired
    private ConsumerTemplate template;

    @Autowired
    private CamelContext context;

    @Test
    public void testTemplateMaximumCache() throws Exception {
        assertNotNull("Should have injected a consumer template", template);

        ConsumerTemplate lookup = context.getRegistry().lookupByNameAndType("template", ConsumerTemplate.class);
        assertNotNull("Should lookup consumer template", lookup);

        assertEquals(50, template.getMaximumCacheSize());
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache around 50 producers to avoid it eating too much memory
        for (int i = 0; i <= 55; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            template.receiveNoWait(e);
        }

        // the eviction is async so force cleanup
        template.cleanUp();
        await().atMost(2, TimeUnit.SECONDS).until(() -> template.getCurrentCacheSize() == 50);
        assertEquals("Size should be 50", 50, template.getCurrentCacheSize());

        // should be 0
        template.stop();
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }

}
