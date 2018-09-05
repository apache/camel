/**
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
package org.apache.camel.component.kubernetes.cluster;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesClusterEvent;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLeadershipController;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLockConfiguration;
import org.apache.camel.impl.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;

/**
 * The cluster view on a specific Camel cluster namespace (not to be confused with Kubernetes namespaces).
 * Namespaces are represented as keys in a Kubernetes ConfigMap (values are the current leader pods).
 */
public class KubernetesClusterView extends AbstractCamelClusterView {

    private CamelContext camelContext;

    private KubernetesClient kubernetesClient;

    private KubernetesConfiguration configuration;

    private KubernetesLockConfiguration lockConfiguration;

    private KubernetesClusterMember localMember;

    private Map<String, KubernetesClusterMember> memberCache;

    private volatile Optional<CamelClusterMember> currentLeader = Optional.empty();

    private volatile List<CamelClusterMember> currentMembers = Collections.emptyList();

    private KubernetesLeadershipController controller;

    public KubernetesClusterView(CamelContext camelContext, KubernetesClusterService cluster, KubernetesConfiguration configuration, KubernetesLockConfiguration lockConfiguration) {
        super(cluster, lockConfiguration.getGroupName());
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.configuration = ObjectHelper.notNull(configuration, "configuration");
        this.lockConfiguration = ObjectHelper.notNull(lockConfiguration, "lockConfiguration");
        this.localMember = new KubernetesClusterMember(lockConfiguration.getPodName());
        this.memberCache = new HashMap<>();
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        return currentLeader;
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        return currentMembers;
    }

    @Override
    protected void doStart() throws Exception {
        if (controller == null) {
            this.kubernetesClient = KubernetesHelper.getKubernetesClient(configuration);

            controller = new KubernetesLeadershipController(camelContext, kubernetesClient, this.lockConfiguration, event -> {
                if (event instanceof KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent) {
                    // New leader
                    Optional<String> leader = KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent.class.cast(event).getData();
                    currentLeader = leader.map(this::toMember);
                    fireLeadershipChangedEvent(currentLeader);
                } else if (event instanceof KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent) {
                    Set<String> members = KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent.class.cast(event).getData();
                    Set<String> oldMembers = currentMembers.stream().map(CamelClusterMember::getId).collect(Collectors.toSet());
                    currentMembers = members.stream().map(this::toMember).collect(Collectors.toList());

                    // Computing differences
                    Set<String> added = new HashSet<>(members);
                    added.removeAll(oldMembers);

                    Set<String> removed = new HashSet<>(oldMembers);
                    removed.removeAll(members);

                    for (String id : added) {
                        fireMemberAddedEvent(toMember(id));
                    }

                    for (String id : removed) {
                        fireMemberRemovedEvent(toMember(id));
                    }
                }
            });

            controller.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (controller != null) {
            controller.stop();
            controller = null;
            kubernetesClient.close();
            kubernetesClient = null;
        }
    }

    protected KubernetesClusterMember toMember(String name) {
        if (name.equals(localMember.getId())) {
            return localMember;
        }
        return memberCache.computeIfAbsent(name, KubernetesClusterMember::new);
    }

    class KubernetesClusterMember implements CamelClusterMember {

        private String podName;

        public KubernetesClusterMember(String podName) {
            this.podName = ObjectHelper.notNull(podName, "podName");
        }

        @Override
        public boolean isLeader() {
            return currentLeader.isPresent() && currentLeader.get().getId().equals(podName);
        }

        @Override
        public boolean isLocal() {
            return ObjectHelper.equal(lockConfiguration.getPodName(), podName);
        }

        @Override
        public String getId() {
            return podName;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("KubernetesClusterMember{");
            sb.append("podName='").append(podName).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

}
