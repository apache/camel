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
package org.apache.camel.component.etcd3.cloud;

import java.util.List;

import org.apache.camel.cloud.ServiceDefinition;

/**
 * A plain Java object representing the list of services that could be found at a specific revision.
 */
final class Etcd3GetServicesResponse {

    /**
     * The revision at which the list of services has been retrieved.
     */
    private final long revision;
    /**
     * The list of services found.
     */
    private final List<ServiceDefinition> services;

    /**
     * Construct a {@code Etcd3GetServicesResponse} with the given parameters.
     *
     * @param revision the revision at which the list of services has been retrieved.
     * @param services the list of services found.
     */
    Etcd3GetServicesResponse(long revision, List<ServiceDefinition> services) {
        this.revision = revision;
        this.services = services;
    }

    /**
     * @return the revision at which the list of services has been retrieved.
     */
    long getRevision() {
        return revision;
    }

    /**
     * @return the list of services found.
     */
    List<ServiceDefinition> getServices() {
        return services;
    }
}
