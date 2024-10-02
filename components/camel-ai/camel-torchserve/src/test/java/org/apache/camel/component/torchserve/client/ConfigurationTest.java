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
package org.apache.camel.component.torchserve.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigurationTest {

    @AfterEach
    void cleanSystemProperties() {
        System.clearProperty("tsc4j.inference.key");
        System.clearProperty("tsc4j.inference.address");
        System.clearProperty("tsc4j.inference.port");
        System.clearProperty("tsc4j.management.key");
        System.clearProperty("tsc4j.management.address");
        System.clearProperty("tsc4j.management.port");
        System.clearProperty("tsc4j.metrics.address");
        System.clearProperty("tsc4j.metrics.port");
    }

    @Test
    void testLoad() {
        var config = Configuration.load();
        assertNotNull(config);
    }

    @Test
    void testSystemProperties() {
        System.setProperty("tsc4j.inference.key", "aaaaa");
        System.setProperty("tsc4j.inference.address", "https://test.com:8180");
        System.setProperty("tsc4j.inference.port", "8180");
        System.setProperty("tsc4j.management.key", "bbbbb");
        System.setProperty("tsc4j.management.address", "https://test.com:8181");
        System.setProperty("tsc4j.management.port", "8181");
        System.setProperty("tsc4j.metrics.address", "https://test.com:8182");
        System.setProperty("tsc4j.metrics.port", "8182");

        var config = Configuration.load();

        assertEquals("aaaaa", config.getInferenceKey().get());
        assertEquals("https://test.com:8180", config.getInferenceAddress().get());
        assertEquals(8180, config.getInferencePort().get());
        assertEquals("bbbbb", config.getManagementKey().get());
        assertEquals("https://test.com:8181", config.getManagementAddress().get());
        assertEquals(8181, config.getManagementPort().get());
        assertEquals("https://test.com:8182", config.getMetricsAddress().get());
        assertEquals(8182, config.getMetricsPort().get());
    }
}
