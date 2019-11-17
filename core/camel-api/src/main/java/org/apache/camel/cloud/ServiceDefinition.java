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
package org.apache.camel.cloud;

import java.util.Map;

import org.apache.camel.util.StringHelper;

/**
 * Represents a Service.
 *
 * @see ServiceChooser
 * @see ServiceDiscovery
 */
public interface ServiceDefinition {
    String SERVICE_META_PREFIX = "service.";

    // default service meta-data keys
    String SERVICE_META_ID = "service.id";
    String SERVICE_META_NAME = "service.name";
    String SERVICE_META_HOST = "service.host";
    String SERVICE_META_PORT = "service.port";
    String SERVICE_META_ZONE = "service.zone";
    String SERVICE_META_PROTOCOL = "service.protocol";
    String SERVICE_META_PATH = "service.path";

    /**
     * Gets the service id.
     */
    String getId();

    /**
     * Gets the service name.
     */
    String getName();

    /**
     * Gets the IP or hostname of the server hosting the service.
     */
    String getHost();

    /**
     * Gets the port number of the server hosting the service.
     */
    int getPort();

    /**
     * Gets the health.
     */
    ServiceHealth getHealth();

    /**
     * Gets a key/value metadata associated with the service.
     */
    Map<String, String> getMetadata();

    /**
     * Check if a service definition matches.
     */
    default boolean matches(ServiceDefinition other) {
        if (this.equals(other)) {
            return true;
        }

        return getPort() == other.getPort()
            && StringHelper.matches(getName(), other.getName())
            && StringHelper.matches(getId(), other.getId())
            && StringHelper.matches(getHost(), other.getHost());
    }
}
