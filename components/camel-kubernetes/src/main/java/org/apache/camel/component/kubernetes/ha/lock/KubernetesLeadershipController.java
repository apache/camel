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
package org.apache.camel.component.kubernetes.ha.lock;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start the monitors and participate to leader election when no active leaders are present.
 * It communicates changes in leadership and cluster members to the given event handler.
 */
public class KubernetesLeadershipController implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesLeadershipController.class);

    private KubernetesClient kubernetesClient;

    private KubernetesLockConfiguration lockConfiguration;

    private ScheduledExecutorService executor;

    private KubernetesLeaderMonitor leaderMonitor;

    private KubernetesMembersMonitor membersMonitor;

    private Optional<String> currentLeader;

    private Set<String> currentMembers;

    private KubernetesClusterEventHandler eventHandler;

    public KubernetesLeadershipController(KubernetesClient kubernetesClient, KubernetesLockConfiguration lockConfiguration, KubernetesClusterEventHandler eventHandler) {

        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandler = eventHandler;

        this.currentLeader = Optional.empty();
        this.currentMembers = Collections.emptySet();
    }

    @Override
    public void start() throws Exception {

        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(); // No concurrency
            leaderMonitor = new KubernetesLeaderMonitor(this.executor, this.kubernetesClient, this.lockConfiguration);
            membersMonitor = new KubernetesMembersMonitor(this.executor, this.kubernetesClient, this.lockConfiguration);

            leaderMonitor.addClusterEventHandler(e -> executor.execute(() -> onLeaderChanged(e)));
            if (eventHandler != null) {
                leaderMonitor.addClusterEventHandler(eventHandler);
            }

            membersMonitor.addClusterEventHandler(e -> executor.execute(() -> onMembersChanged(e)));
            if (eventHandler != null) {
                membersMonitor.addClusterEventHandler(eventHandler);
            }

            // Start all services
            leaderMonitor.start();
            membersMonitor.start();

            // Fire a new election if possible
            executor.execute(this::runLeaderElection);
        }

    }

    @Override
    public void stop() throws Exception {
        if (executor != null) {
            membersMonitor.stop();
            leaderMonitor.stop();
            executor.shutdown();
            executor.shutdownNow();

            membersMonitor = null;
            leaderMonitor = null;
            executor = null;
        }
    }

    private void onLeaderChanged(KubernetesClusterEvent e) {
        Optional<String> newLeader = KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent.class.cast(e).getData();
        if (!newLeader.isPresent()) {
            executor.execute(this::tryLeaderElection);
        }
        this.currentLeader = newLeader;
    }

    private void onMembersChanged(KubernetesClusterEvent e) {
        Set<String> newMembers = KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent.class.cast(e).getData();
        if (currentLeader.isPresent()) {
            // Check if the current leader is still present in the list
            if (!newMembers.contains(currentLeader.get()) && currentMembers.contains(currentLeader.get())) {
                executor.execute(this::runLeaderElection);
            }
        }
        this.currentMembers = newMembers;
    }

    private void runLeaderElection() {
        boolean finished = false;
        try {
            finished = tryLeaderElection();
        } catch (Exception ex) {
            LOG.warn("Exception while trying to acquire the leadership", ex);
        }

        if (!finished) {
            executor.schedule(this::runLeaderElection, 1, TimeUnit.SECONDS);
        }
    }

    private boolean tryLeaderElection() {
        LOG.debug("Starting leader election");
        if (!currentMembers.contains(this.lockConfiguration.getPodName())) {
            LOG.debug("The current pod ({}) is not in the list of participating pods {}. Cannot participate to the election", this.lockConfiguration.getPodName(), currentMembers);
            return false;
        }

        ConfigMap configMap = kubernetesClient.configMaps()
                .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withName(this.lockConfiguration.getConfigMapName())
                .get();

        if (configMap == null) {
            // No configmap created so far
            LOG.info("Lock configmap is not present in the Kubernetes namespace. A new ConfigMap will be created");

            ConfigMap newConfigMap = new ConfigMapBuilder().
                    withNewMetadata()
                    .withName(this.lockConfiguration.getConfigMapName())
                    .addToLabels("provider", "camel")
                    .addToLabels("kind", "locks").
                            endMetadata()
                    .addToData(this.lockConfiguration.getGroupName(), this.lockConfiguration.getPodName())
                    .build();

            try {
                kubernetesClient.configMaps()
                        .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                        .create(newConfigMap);
            } catch (Exception ex) {
                // Suppress exception
                LOG.warn("Unable to create the ConfigMap, it may have been created by other cluster members concurrently. If the problem persists, check if the service account has the right "
                        + "permissions to create it");
                LOG.debug("Exception while trying to create the ConfigMap", ex);
                return false;
            }
            return true;
        } else {
            LOG.info("Lock configmap already present in the Kubernetes namespace. Checking...");
            Map<String, String> leaders = configMap.getData();
            Optional<String> oldLeader = leaders != null ? Optional.ofNullable(leaders.get(this.lockConfiguration.getGroupName())) : Optional.empty();

            boolean noLeaderPresent = !oldLeader.isPresent() || !currentMembers.contains(oldLeader.get());
            boolean alreadyLeader = oldLeader.isPresent() && oldLeader.get().equals(this.lockConfiguration.getPodName());

            if (noLeaderPresent && !alreadyLeader) {
                LOG.info("Trying to acquire the lock in configmap={}, key={}", this.lockConfiguration.getConfigMapName(), this.lockConfiguration.getGroupName());
                ConfigMap newConfigMap = new ConfigMapBuilder(configMap)
                        .addToData(this.lockConfiguration.getGroupName(), this.lockConfiguration.getPodName())
                        .build();

                kubernetesClient.configMaps()
                        .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                        .withName(this.lockConfiguration.getConfigMapName())
                        .lockResourceVersion(configMap.getMetadata().getResourceVersion())
                        .replace(newConfigMap);

                LOG.info("Lock acquired for configmap={}, key={}", this.lockConfiguration.getConfigMapName(), this.lockConfiguration.getGroupName());
            } else if (!noLeaderPresent) {
                LOG.info("A leader is already present for configmap={}, key={}: {}", this.lockConfiguration.getConfigMapName(), this.lockConfiguration.getGroupName(), oldLeader);
            } else {
                LOG.info("This pod ({}) is already the leader for configmap={}, key={}", this.lockConfiguration.getPodName(), this.lockConfiguration.getConfigMapName(), this.lockConfiguration
                        .getGroupName());
            }
            return true;
        }
    }

}
