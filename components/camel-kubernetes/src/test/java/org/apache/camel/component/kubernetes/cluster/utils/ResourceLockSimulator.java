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

import io.fabric8.kubernetes.api.model.HasMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central lock for testing leader election.
 */
public abstract class ResourceLockSimulator<T extends HasMetadata> {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceLockSimulator.class);

    private String resourceName;

    private T currentResource;

    private long versionCounter = 1000000;

    public ResourceLockSimulator(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceName() {
        return resourceName;
    }

    public synchronized boolean setResource(T resource, boolean insert) {
        // Insert
        if (insert && currentResource != null) {
            LOG.error("Current resource should have been null");
            return false;
        }

        // Update
        if (!insert && currentResource == null) {
            LOG.error("Current resource should not have been null");
            return false;
        }
        String version = resource.getMetadata() != null ? resource.getMetadata().getResourceVersion() : null;
        if (version != null) {
            long versionLong = Long.parseLong(version);
            if (versionLong != versionCounter) {
                LOG.warn("Current resource version is {} while the update is related to version {}", versionCounter,
                        versionLong);
                return false;
            }
        }

        this.currentResource = withNewResourceVersion(resource, String.valueOf(++versionCounter));
        return true;
    }

    public synchronized T getResource() {
        if (currentResource == null) {
            return null;
        }

        return copyOf(currentResource);
    }

    protected abstract T withNewResourceVersion(T resource, String newResourceVersion);

    protected abstract T copyOf(T resource);

    public abstract String getAPIPath();

    public abstract String getResourcePath();

    public abstract Class<T> getResourceClass();

}
