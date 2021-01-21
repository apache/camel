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
package org.apache.camel.component.infinispan.embedded;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.context.Flag;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanEmbeddedConfigurationTest {
    @Test
    public void embeddedCacheWithFlagsTest() throws Exception {
        InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();
        configuration.setFlags(Flag.IGNORE_RETURN_VALUES);

        try (InfinispanEmbeddedManager manager = new InfinispanEmbeddedManager(configuration)) {
            manager.start();

            BasicCache<Object, Object> cache = manager.getCache("default");
            assertNotNull(cache);

            assertTrue(cache instanceof Cache);
            assertTrue(cache instanceof AdvancedCache);

            String key = UUID.randomUUID().toString();
            assertNull(cache.put(key, "val1"));

            // TODO: as we are testing a local cache,
            //assertNull(cache.put(key, "val2"));
        }
    }
}
