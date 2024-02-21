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
package org.apache.camel.component.kubernetes.cluster.lock.impl;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLeaseResourceManager;
import org.apache.camel.component.kubernetes.cluster.lock.LeaderInfo;

public class NativeLeaseResourceManager implements KubernetesLeaseResourceManager<Lease> {

    @Override
    public LeaderInfo decodeLeaderInfo(Lease lease, Set<String> members, String group) {
        return new LeaderInfo(group, getLeader(lease), getLocalTimestamp(lease), members, getLeaseDurationSeconds(lease));
    }

    @Override
    public Lease fetchLeaseResource(KubernetesClient client, String namespace, String name, String group) {
        return client.leases()
                .inNamespace(namespace)
                .withName(leaseResourceName(name, group)).get();
    }

    @Override
    public Lease optimisticDeleteLeaderInfo(KubernetesClient client, Lease leaseResource, String group) {
        Lease updatedLease = getLeaseWithoutLeader(leaseResource);
        return client.leases()
                .inNamespace(leaseResource.getMetadata().getNamespace())
                .resource(updatedLease)
                .lockResourceVersion(leaseResource.getMetadata().getResourceVersion()).update();
    }

    @Override
    public Lease optimisticAcquireLeadership(KubernetesClient client, Lease leaseResource, LeaderInfo newLeaderInfo) {
        Lease updatedLease = getLeaseWithNewLeader(leaseResource, newLeaderInfo);
        return client.leases()
                .inNamespace(leaseResource.getMetadata().getNamespace())
                .resource(updatedLease)
                .lockResourceVersion(leaseResource.getMetadata().getResourceVersion()).update();
    }

    @Override
    public Lease refreshLeaseRenewTime(KubernetesClient client, Lease leaseResource, int minUpdateIntervalSeconds) {
        ZonedDateTime lastRenew = leaseResource.getSpec() != null ? leaseResource.getSpec().getRenewTime() : null;
        if (lastRenew == null || lastRenew.plusSeconds(minUpdateIntervalSeconds).isBefore(ZonedDateTime.now())) {
            Lease updatedLease = new LeaseBuilder(leaseResource)
                    .editOrNewSpec()
                    .withRenewTime(ZonedDateTime.now())
                    .endSpec()
                    .build();
            return client.leases()
                    .inNamespace(leaseResource.getMetadata().getNamespace())
                    .resource(updatedLease)
                    .lockResourceVersion(leaseResource.getMetadata().getResourceVersion()).update();
        }
        return leaseResource;
    }

    @Override
    public Lease createNewLeaseResource(KubernetesClient client, String namespace, String prefix, LeaderInfo leaderInfo) {
        ZonedDateTime now = ZonedDateTime.now();
        Lease newLease = new LeaseBuilder().withNewMetadata()
                .withName(leaseResourceName(prefix, leaderInfo.getGroupName()))
                .addToLabels("provider", "camel")
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity(leaderInfo.getLeader())
                .withAcquireTime(now)
                .withLeaseDurationSeconds(leaderInfo.getLeaseDurationSeconds())
                .withRenewTime(now)
                .endSpec()
                .build();

        return client.leases()
                .inNamespace(namespace)
                .resource(newLease)
                .create();
    }

    private static Lease getLeaseWithNewLeader(Lease lease, LeaderInfo leaderInfo) {
        Integer transitions = lease.getSpec() != null ? lease.getSpec().getLeaseTransitions() : null;
        if (transitions == null) {
            transitions = 0;
        }
        ZonedDateTime now = ZonedDateTime.now();
        return new LeaseBuilder(lease)
                .editOrNewSpec()
                .withHolderIdentity(leaderInfo.getLeader())
                .withAcquireTime(now)
                .withLeaseDurationSeconds(leaderInfo.getLeaseDurationSeconds())
                .withRenewTime(now)
                .withLeaseTransitions(transitions + 1)
                .endSpec()
                .build();
    }

    private static Lease getLeaseWithoutLeader(Lease lease) {
        return new LeaseBuilder(lease).editOrNewSpec()
                .withHolderIdentity(null)
                .withAcquireTime(null)
                .withRenewTime(null)
                .withLeaseDurationSeconds(null)
                .endSpec()
                .build();
    }

    private static Date getLocalTimestamp(Lease lease) {
        if (lease == null || lease.getSpec() == null || lease.getSpec().getAcquireTime() == null) {
            return null;
        }
        return Date.from(lease.getSpec().getAcquireTime().toInstant());
    }

    private static Integer getLeaseDurationSeconds(Lease lease) {
        if (lease == null || lease.getSpec() == null) {
            return null;
        }
        return lease.getSpec().getLeaseDurationSeconds();
    }

    private static String getLeader(Lease lease) {
        if (lease == null || lease.getSpec() == null) {
            return null;
        }
        return lease.getSpec().getHolderIdentity();
    }

    private static String leaseResourceName(String prefix, String group) {
        return toValidKubernetesID(prefix + "-" + group);
    }

    private static String toValidKubernetesID(String id) {
        id = id.toLowerCase().replaceAll("[^a-z0-9-.]", "-");
        while (id.length() > 0 && isNonAlphanumeric(id, 0)) {
            id = id.substring(1);
        }
        while (id.length() > 0 && isNonAlphanumeric(id, id.length() - 1)) {
            id = id.substring(0, id.length() - 1);
        }
        return id;
    }

    private static boolean isNonAlphanumeric(String id, int pos) {
        return !Character.isAlphabetic(id.charAt(pos)) && !Character.isDigit(id.charAt(pos));
    }
}
