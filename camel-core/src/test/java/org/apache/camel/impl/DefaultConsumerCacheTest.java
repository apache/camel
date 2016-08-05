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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;

/**
 * @version 
 */
public class DefaultConsumerCacheTest extends ContextTestSupport {

    public void testCacheConsumers() throws Exception {
        ConsumerCache cache = new ConsumerCache(this, context);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        // test that we cache at most 1000 consumers to avoid it eating to much memory
        for (int i = 0; i < 1003; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            PollingConsumer p = cache.getConsumer(e);
            assertNotNull("the polling consumer should not be null", p);
        }

        // the eviction is async so force cleanup
        cache.cleanUp();

        assertEquals("Size should be 1000", 1000, cache.size());
        cache.stop();
    }

}