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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;

/**
 * @version 
 */
public class DefaultProducerTemplateWithCustomCacheMaxSizeTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getProperties().put(Exchange.MAXIMUM_CACHE_POOL_SIZE, "200");
        return context;
    }

    public void testCacheProducers() throws Exception {
        ProducerTemplate template = context.createProducerTemplate();

        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache at most 500 producers to avoid it eating to much memory
        for (int i = 0; i < 203; i++) {
            Endpoint e = context.getEndpoint("seda:queue:" + i);
            template.sendBody(e, "Hello");
        }

        // the eviction is async so force cleanup
        template.cleanUp();

        assertEquals("Size should be 200", 200, template.getCurrentCacheSize());
        template.stop();

        // should be 0
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }

    public void testInvalidSizeABC() {
        context.getProperties().put(Exchange.MAXIMUM_CACHE_POOL_SIZE, "ABC");
        try {
            context.createProducerTemplate();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals("Property CamelMaximumCachePoolSize must be a positive number, was: ABC", e.getMessage());
        }
    }

    public void testInvalidSizeZero() {
        context.getProperties().put(Exchange.MAXIMUM_CACHE_POOL_SIZE, "0");
        try {
            context.createProducerTemplate();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals("Property CamelMaximumCachePoolSize must be a positive number, was: 0", e.getMessage());
        }
    }
}
