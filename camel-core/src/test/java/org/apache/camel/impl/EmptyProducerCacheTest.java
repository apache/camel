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
import org.apache.camel.Producer;

public class EmptyProducerCacheTest extends ContextTestSupport {

    public void testEmptyCache() throws Exception {
        ProducerCache cache = new EmptyProducerCache(this, context);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        // we never cache any producers
        Endpoint e = context.getEndpoint("direct:queue:1");
        Producer p = cache.acquireProducer(e);

        assertEquals("Size should be 0", 0, cache.size());

        cache.releaseProducer(e, p);

        assertEquals("Size should be 0", 0, cache.size());

        cache.stop();
    }

    public void testCacheProducerAcquireAndRelease() throws Exception {
        ProducerCache cache = new EmptyProducerCache(this, context);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        // we never cache any producers
        for (int i = 0; i < 1003; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            Producer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        assertEquals("Size should be 1000", 0, cache.size());
        cache.stop();
    }

}
