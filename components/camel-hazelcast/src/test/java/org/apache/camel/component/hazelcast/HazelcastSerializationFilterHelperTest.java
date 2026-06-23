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
package org.apache.camel.component.hazelcast;

import com.hazelcast.config.ClassFilter;
import com.hazelcast.config.Config;
import com.hazelcast.config.JavaSerializationFilterConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastSerializationFilterHelperTest {

    @Test
    void appliesDefaultWhenNoneConfigured() {
        Config config = new Config();
        assertNull(config.getSerializationConfig().getJavaSerializationFilterConfig());

        HazelcastSerializationFilterHelper.applyDefault(config);

        JavaSerializationFilterConfig filter = config.getSerializationConfig().getJavaSerializationFilterConfig();
        assertNotNull(filter);

        ClassFilter whitelist = filter.getWhitelist();
        assertNotNull(whitelist);
        assertTrue(whitelist.getPrefixes().contains("java."));
        assertTrue(whitelist.getPrefixes().contains("javax."));
        assertTrue(whitelist.getPrefixes().contains("org.apache.camel."));

        ClassFilter blacklist = filter.getBlacklist();
        assertNotNull(blacklist);
        assertTrue(blacklist.getPrefixes().contains("java.net."));
    }

    @Test
    void respectsExistingUserConfiguration() {
        Config config = new Config();
        JavaSerializationFilterConfig userFilter = new JavaSerializationFilterConfig();
        userFilter.setWhitelist(new ClassFilter().addPrefixes("com.example."));
        config.getSerializationConfig().setJavaSerializationFilterConfig(userFilter);

        HazelcastSerializationFilterHelper.applyDefault(config);

        JavaSerializationFilterConfig actual = config.getSerializationConfig().getJavaSerializationFilterConfig();
        assertSame(userFilter, actual);
        assertEquals(1, actual.getWhitelist().getPrefixes().size());
        assertTrue(actual.getWhitelist().getPrefixes().contains("com.example."));
    }

    @Test
    void handlesNullConfigGracefully() {
        assertDoesNotThrow(() -> HazelcastSerializationFilterHelper.applyDefault(null));
    }
}
