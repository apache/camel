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
package org.apache.camel.component.kubernetes.cluster.lock;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kubernetes.cluster.LeaseResourceType;
import org.apache.camel.component.kubernetes.cluster.lock.impl.ConfigMapLeaseResourceManager;
import org.apache.camel.component.kubernetes.cluster.lock.impl.NativeLeaseResourceManager;

/**
 * Handles the actual interaction with Kubernetes resources, allowing different implementation to be plugged.
 */
public interface KubernetesLeaseResourceManager<T extends HasMetadata> {

    /**
     * Create a new {@link KubernetesLeaseResourceManager} of the given {@link LeaseResourceType}.
     */
    @SuppressWarnings("unchecked")
    static <S extends HasMetadata> KubernetesLeaseResourceManager<S> create(LeaseResourceType type) {
        switch (type) {
            case ConfigMap:
                return (KubernetesLeaseResourceManager<S>) new ConfigMapLeaseResourceManager();
            case Lease:
                return (KubernetesLeaseResourceManager<S>) new NativeLeaseResourceManager();
            default:
                throw new RuntimeCamelException("Unsupported lease resource type " + type);
        }
    }

    /**
     * Return a {@link LeaderInfo} object from the underlying Kubernetes resource.
     */
    LeaderInfo decodeLeaderInfo(T leaseResource, Set<String> members, String group);

    /**
     * Fetch the lease resource for the given name and group.
     */
    T fetchLeaseResource(KubernetesClient client, String namespace, String leaseResourceName, String group);

    /**
     * Delete leadership information for the given lease resource and group.
     */
    T optimisticDeleteLeaderInfo(KubernetesClient client, T leaseResource, String group);

    /**
     * Set the leadership information on the lease resource to match the given {@link LeaderInfo}.
     */
    T optimisticAcquireLeadership(KubernetesClient client, T leaseResource, LeaderInfo newLeaderInfo);

    /**
     * Create a new lease resource matching the given {@link LeaderInfo}.
     */
    T createNewLeaseResource(KubernetesClient client, String namespace, String leaseResourceName, LeaderInfo leaderInfo);

    /**
     * Update information on the lease resource to increase the renew time (if last renewal has occurred more than
     * minUpdateIntervalSeconds seconds ago).
     */
    T refreshLeaseRenewTime(KubernetesClient client, T leaseResource, int minUpdateIntervalSeconds);

}
