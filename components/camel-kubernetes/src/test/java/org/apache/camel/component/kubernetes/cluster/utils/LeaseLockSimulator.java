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

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;

/**
 * Central lock for testing leader election based on Lease.
 */
public class LeaseLockSimulator extends ResourceLockSimulator<Lease> {

    public LeaseLockSimulator(String resourceName) {
        super(resourceName);
    }

    @Override
    protected Lease withNewResourceVersion(Lease resource, String newResourceVersion) {
        return new LeaseBuilder(resource).editOrNewMetadata().withResourceVersion(newResourceVersion)
                .endMetadata().build();
    }

    @Override
    protected Lease copyOf(Lease resource) {
        return new LeaseBuilder(resource).build();
    }

    @Override
    public String getResourcePath() {
        return "leases";
    }

    @Override
    public String getAPIPath() {
        return "/apis/coordination.k8s.io/v1";
    }

    @Override
    public Class<Lease> getResourceClass() {
        return Lease.class;
    }
}
