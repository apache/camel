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
package org.apache.camel.maven.component.vertx.kafka.config;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.TimeUtils;
import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUtilsTest {

    @Test
    void testExtractOnlyConsumerFields() {
        final Set<String> consumerConfigs = new HashSet<>();
        consumerConfigs.add("bootstrap.servers");
        consumerConfigs.add("poll.timeout");

        final Set<String> producerConfigs = new HashSet<>();
        producerConfigs.add("bootstrap.servers");
        producerConfigs.add("push.timeout");

        final Set<String> fields = ConfigUtils.extractConsumerOnlyFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.contains("poll.timeout"));
    }

    @Test
    void testExtractOnlyConsumerFieldsWithConfigKeyFields() {
        final Map<String, ConfigDef.ConfigKey> consumerConfigs = new HashMap<>();
        consumerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        consumerConfigs.put("poll.timeout", createConfigKey("poll.timeout"));

        final Map<String, ConfigDef.ConfigKey> producerConfigs = new HashMap<>();
        producerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        producerConfigs.put("push.timeout", createConfigKey("push.timeout"));

        final Map<String, ConfigDef.ConfigKey> fields = ConfigUtils.extractConsumerOnlyFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.containsKey("poll.timeout"));
    }

    @Test
    void testExtractOnlyProducerFields() {
        final Set<String> consumerConfigs = new HashSet<>();
        consumerConfigs.add("bootstrap.servers");
        consumerConfigs.add("poll.timeout");

        final Set<String> producerConfigs = new HashSet<>();
        producerConfigs.add("bootstrap.servers");
        producerConfigs.add("push.timeout");

        final Set<String> fields = ConfigUtils.extractProducerOnlyFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.contains("push.timeout"));
    }

    @Test
    void testExtractOnlyProducerFieldsWithConfigKeyFields() {
        final Map<String, ConfigDef.ConfigKey> consumerConfigs = new HashMap<>();
        consumerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        consumerConfigs.put("poll.timeout", createConfigKey("poll.timeout"));

        final Map<String, ConfigDef.ConfigKey> producerConfigs = new HashMap<>();
        producerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        producerConfigs.put("push.timeout", createConfigKey("push.timeout"));

        final Map<String, ConfigDef.ConfigKey> fields = ConfigUtils.extractProducerOnlyFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.containsKey("push.timeout"));
    }

    @Test
    void testExtractOnlyCommonFields() {
        final Set<String> consumerConfigs = new HashSet<>();
        consumerConfigs.add("bootstrap.servers");
        consumerConfigs.add("poll.timeout");

        final Set<String> producerConfigs = new HashSet<>();
        producerConfigs.add("bootstrap.servers");
        producerConfigs.add("push.timeout");

        final Set<String> fields = ConfigUtils.extractCommonFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.contains("bootstrap.servers"));
    }

    @Test
    void testExtractOnlyCommonFieldsWithConfigKeyFields() {
        final Map<String, ConfigDef.ConfigKey> consumerConfigs = new HashMap<>();
        consumerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        consumerConfigs.put("poll.timeout", createConfigKey("poll.timeout"));

        final Map<String, ConfigDef.ConfigKey> producerConfigs = new HashMap<>();
        producerConfigs.put("bootstrap.servers", createConfigKey("bootstrap.servers"));
        producerConfigs.put("push.timeout", createConfigKey("push.timeout"));

        final Map<String, ConfigDef.ConfigKey> fields = ConfigUtils.extractCommonFields(consumerConfigs, producerConfigs);
        assertEquals(1, fields.size());
        assertTrue(fields.containsKey("bootstrap.servers"));
    }

    @Test
    void testToTimeAsString() {
        assertEquals("600ms", ConfigUtils.toTimeAsString(TimeUtils.toMilliSeconds("600ms")));
        assertEquals("0ms", ConfigUtils.toTimeAsString(TimeUtils.toMilliSeconds("0ms")));
        assertEquals("1s", ConfigUtils.toTimeAsString(TimeUtils.toMilliSeconds("1000ms")));
        assertEquals("1m600ms", ConfigUtils.toTimeAsString(TimeUtils.toMilliSeconds("1m600ms")));
        assertEquals("1m1s100ms", ConfigUtils.toTimeAsString(TimeUtils.toMilliSeconds("1m1100ms")));
        assertEquals("5m10s300ms", ConfigUtils.toTimeAsString(310300));
        assertEquals("5s500ms", ConfigUtils.toTimeAsString(5500));
        assertEquals("1h50m", ConfigUtils.toTimeAsString(6600000));
        assertEquals("2d3h4m", ConfigUtils.toTimeAsString(Duration.parse("P2DT3H4M").toMillis()));
        assertEquals("2d4m", ConfigUtils.toTimeAsString(Duration.parse("P2DT4M").toMillis()));
    }

    private ConfigDef.ConfigKey createConfigKey(final String name) {
        return new ConfigDef.ConfigKey(
                name, ConfigDef.Type.STRING, null,
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);
    }
}
