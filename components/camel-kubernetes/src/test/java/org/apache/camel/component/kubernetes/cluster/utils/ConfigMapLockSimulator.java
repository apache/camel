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
package org.apache.camel.component.kubernetes.cluster.utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central lock for testing leader election.
 */
public class ConfigMapLockSimulator {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMapLockSimulator.class);

    private String configMapName;

    private ConfigMap currentMap;

    private long versionCounter = 1000000;

    public ConfigMapLockSimulator(String configMapName) {
        this.configMapName = configMapName;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public synchronized boolean setConfigMap(ConfigMap map, boolean insert) {
        // Insert
        if (insert && currentMap != null) {
            LOG.error("Current map should have been null");
            return false;
        }

        // Update
        if (!insert && currentMap == null) {
            LOG.error("Current map should not have been null");
            return false;
        }
        String version = map.getMetadata() != null ? map.getMetadata().getResourceVersion() : null;
        if (version != null) {
            long versionLong = Long.parseLong(version);
            if (versionLong != versionCounter) {
                LOG.warn("Current resource version is {} while the update is related to version {}", versionCounter, versionLong);
                return false;
            }
        }

        this.currentMap = new ConfigMapBuilder(map).editOrNewMetadata().withResourceVersion(String.valueOf(++versionCounter)).endMetadata().build();
        return true;
    }

    public synchronized ConfigMap getConfigMap() {
        if (currentMap == null) {
            return null;
        }

        return new ConfigMapBuilder(currentMap).build();
    }

}
