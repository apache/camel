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
package org.apache.camel.component.kubernetes.cluster;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelPreemptiveClusterView;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesClusterEvent;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLeadershipController;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLockConfiguration;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;

/**
 * The cluster view on a specific Camel cluster namespace (not to be confused with Kubernetes namespaces). Namespaces
 * are represented as keys in a Kubernetes ConfigMap (values are the current leader pods).
 */
public class KubernetesClusterView extends AbstractCamelClusterView implements CamelPreemptiveClusterView {

    private CamelContext camelContext;
    private final KubernetesClusterService kubernetesCluster;
    private KubernetesClient kubernetesClient;
    // whether the view created kubernetesClient (closed when the view stops), or the client was set on the
    // configuration (owned by the user, and shared with the other views of the cluster service)
    private boolean closeClient;
    private KubernetesConfiguration configuration;
    private KubernetesLockConfiguration lockConfiguration;
    private KubernetesClusterMember localMember;
    private Map<String, KubernetesClusterMember> memberCache;
    private volatile CamelClusterMember currentLeader = null;
    private volatile List<CamelClusterMember> currentMembers = Collections.emptyList();
    private KubernetesLeadershipController controller;
    private boolean disabled;
    // guards generation and the updates of currentLeader by the leadership events against doStop
    private final Object leaderLock = new Object();
    // incremented on every start and stop: the events of a controller belong to the generation of the start that
    // created it, and are ignored once that generation is over
    private long generation;

    public KubernetesClusterView(CamelContext camelContext, KubernetesClusterService cluster,
                                 KubernetesConfiguration configuration,
                                 KubernetesLockConfiguration lockConfiguration) {
        super(cluster, lockConfiguration.getGroupName());
        this.kubernetesCluster = cluster;
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.configuration = ObjectHelper.notNull(configuration, "configuration");
        this.lockConfiguration = ObjectHelper.notNull(lockConfiguration, "lockConfiguration");
        this.localMember = new KubernetesClusterMember(lockConfiguration.getPodName());
        this.memberCache = new HashMap<>();
        this.disabled = false;
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        return Optional.ofNullable(currentLeader);
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        return currentMembers;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (this.controller != null) {
            this.controller.setDisabled(disabled);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (controller == null) {
            this.closeClient = configuration.getKubernetesClient() == null;
            this.kubernetesClient = KubernetesHelper.getKubernetesClient(configuration);
            final long gen;
            synchronized (leaderLock) {
                gen = ++generation;
            }

            controller = new KubernetesLeadershipController(camelContext, kubernetesClient, this.lockConfiguration, event -> {
                if (event instanceof KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent) {
                    // New leader
                    Optional<String> leader
                            = KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent.class.cast(event).getData();
                    CamelClusterMember newLeader = leader.map(this::toMember).orElse(null);
                    synchronized (leaderLock) {
                        if (gen != generation) {
                            // the view has been stopped
                            return;
                        }
                        currentLeader = newLeader;
                    }
                    fireLeadershipChangedEvent(newLeader);
                } else if (event instanceof KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent) {
                    synchronized (leaderLock) {
                        if (gen != generation) {
                            return;
                        }
                    }
                    Set<String> members
                            = KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent.class.cast(event).getData();
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

            this.controller.setDisabled(disabled);
            controller.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (controller != null) {
            KubernetesLeadershipController stopping = controller;
            controller = null;
            synchronized (leaderLock) {
                generation++;
            }
            // the wait for a running refresh and the release take at most the renew deadline, and while the cluster
            // service or the CamelContext stops, at most the renew deadline for all the views together
            long stopStart = System.nanoTime();
            long timeoutMillis = lockConfiguration.getRenewDeadlineMillis();
            Long sharedTimeLeft = kubernetesCluster.sharedStopTimeLeftNanos();
            if (sharedTimeLeft != null) {
                timeoutMillis = Math.min(timeoutMillis, TimeUnit.NANOSECONDS.toMillis(sharedTimeLeft));
            }
            try {
                // no more refreshes of the lease, and no more leadership events
                stopping.stop(timeoutMillis);

                boolean wasLeader;
                synchronized (leaderLock) {
                    wasLeader = localMember.isLeader();
                    currentLeader = null;
                }
                if (wasLeader) {
                    // tell the listeners that this pod is no longer the leader, before the lease is released and another
                    // pod can take over the leadership
                    fireLeadershipChangedEvent((CamelClusterMember) null);
                }
                stopping.releaseLeadership();
            } finally {
                if (closeClient) {
                    kubernetesClient.close();
                }
                kubernetesClient = null;
                if (sharedTimeLeft != null) {
                    kubernetesCluster.sharedStopTimeSpent(System.nanoTime() - stopStart);
                }
            }
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
            return currentLeader != null && currentLeader.getId().equals(podName);
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
