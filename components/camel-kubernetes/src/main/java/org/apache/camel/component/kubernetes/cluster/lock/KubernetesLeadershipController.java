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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors current status and participate to leader election when no active
 * leaders are present. It communicates changes in leadership and cluster
 * members to the given event handler.
 */
public class KubernetesLeadershipController implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesLeadershipController.class);

    private enum State {
        NOT_LEADER, BECOMING_LEADER, LEADER
    }

    private CamelContext camelContext;

    private KubernetesClient kubernetesClient;

    private KubernetesLockConfiguration lockConfiguration;

    private KubernetesClusterEventHandler eventHandler;

    private State currentState = State.NOT_LEADER;

    private ScheduledExecutorService serializedExecutor;

    private TimedLeaderNotifier leaderNotifier;

    private volatile LeaderInfo latestLeaderInfo;
    private volatile ConfigMap latestConfigMap;
    private volatile Set<String> latestMembers;

    public KubernetesLeadershipController(CamelContext camelContext, KubernetesClient kubernetesClient, KubernetesLockConfiguration lockConfiguration,
                                          KubernetesClusterEventHandler eventHandler) {
        this.camelContext = camelContext;
        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandler = eventHandler;
    }

    @Override
    public void start() {
        if (serializedExecutor == null) {
            LOG.debug("{} Starting leadership controller...", logPrefix());
            serializedExecutor = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "CamelKubernetesLeadershipController");
            leaderNotifier = new TimedLeaderNotifier(this.camelContext, this.eventHandler);

            leaderNotifier.start();
            serializedExecutor.execute(this::refreshStatus);
        }
    }

    @Override
    public void stop() {
        LOG.debug("{} Stopping leadership controller...", logPrefix());
        if (serializedExecutor != null) {
            serializedExecutor.shutdownNow();
        }
        serializedExecutor = null;

        if (leaderNotifier != null) {
            leaderNotifier.stop();
        }
        leaderNotifier = null;
    }

    private void refreshStatus() {
        switch (currentState) {
            case NOT_LEADER:
                refreshStatusNotLeader();
                break;
            case BECOMING_LEADER:
                refreshStatusBecomingLeader();
                break;
            case LEADER:
                refreshStatusLeader();
                break;
            default:
                throw new RuntimeException("Unsupported state " + currentState);
        }
    }

    /**
     * This pod is currently not leader. It should monitor the leader
     * configuration and try to acquire the leadership if possible.
     */
    private void refreshStatusNotLeader() {
        LOG.debug("{} Pod is not leader, pulling new data from the cluster", logPrefix());
        boolean pulled = lookupNewLeaderInfo();
        if (!pulled) {
            rescheduleAfterDelay();
            return;
        }

        if (this.latestLeaderInfo.hasEmptyLeader()) {
            // There is no previous leader
            LOG.info("{} The cluster has no leaders. Trying to acquire the leadership...", logPrefix());
            boolean acquired = tryAcquireLeadership();
            if (acquired) {
                LOG.info("{} Leadership acquired by current pod with immediate effect", logPrefix());
                this.currentState = State.LEADER;
                this.serializedExecutor.execute(this::refreshStatus);
                return;
            } else {
                LOG.info("{} Unable to acquire the leadership, it may have been acquired by another pod", logPrefix());
            }
        } else if (!this.latestLeaderInfo.hasValidLeader()) {
            // There's a previous leader and it's invalid
            LOG.info("{} Leadership has been lost by old owner. Trying to acquire the leadership...", logPrefix());
            boolean acquired = tryAcquireLeadership();
            if (acquired) {
                LOG.info("{} Leadership acquired by current pod", logPrefix());
                this.currentState = State.BECOMING_LEADER;
                this.serializedExecutor.execute(this::refreshStatus);
                return;
            } else {
                LOG.info("{} Unable to acquire the leadership, it may have been acquired by another pod", logPrefix());
            }
        } else if (this.latestLeaderInfo.isValidLeader(this.lockConfiguration.getPodName())) {
            // We are leaders for some reason (e.g. pod restart on failure)
            LOG.info("{} Leadership is already owned by current pod", logPrefix());
            this.currentState = State.BECOMING_LEADER;
            this.serializedExecutor.execute(this::refreshStatus);
            return;
        }

        this.leaderNotifier.refreshLeadership(Optional.ofNullable(this.latestLeaderInfo.getLeader()), System.currentTimeMillis(), this.lockConfiguration.getLeaseDurationMillis(),
                this.latestLeaderInfo.getMembers());
        rescheduleAfterDelay();
    }

    /**
     * This pod has acquired the leadership but it should wait for the old
     * leader to tear down resources before starting the local services.
     */
    private void refreshStatusBecomingLeader() {
        // Wait always the same amount of time before becoming the leader
        // Even if the current pod is already leader, we should let a possible
        // old version of the pod to shut down
        long delay = this.lockConfiguration.getLeaseDurationMillis();
        LOG.info("{} Current pod owns the leadership, but it will be effective in {} seconds...", logPrefix(),
                new BigDecimal(delay).divide(BigDecimal.valueOf(1000), 2, BigDecimal.ROUND_HALF_UP));

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            LOG.warn("Thread interrupted", e);
        }

        LOG.info("{} Current pod is becoming the new leader now...", logPrefix());
        this.currentState = State.LEADER;
        this.serializedExecutor.execute(this::refreshStatus);
    }

    private void refreshStatusLeader() {
        LOG.debug("{} Pod should be the leader, pulling new data from the cluster", logPrefix());
        long timeBeforePulling = System.currentTimeMillis();
        boolean pulled = lookupNewLeaderInfo();
        if (!pulled) {
            rescheduleAfterDelay();
            return;
        }

        if (this.latestLeaderInfo.isValidLeader(this.lockConfiguration.getPodName())) {
            LOG.debug("{} Current Pod is still the leader", logPrefix());
            this.leaderNotifier.refreshLeadership(Optional.of(this.lockConfiguration.getPodName()), timeBeforePulling, this.lockConfiguration.getRenewDeadlineMillis(),
                    this.latestLeaderInfo.getMembers());
            rescheduleAfterDelay();
            return;
        } else {
            LOG.debug("{} Current Pod has lost the leadership", logPrefix());
            this.currentState = State.NOT_LEADER;
            // set a empty leader to signal leadership loss
            this.leaderNotifier.refreshLeadership(Optional.empty(), System.currentTimeMillis(), lockConfiguration.getLeaseDurationMillis(), this.latestLeaderInfo.getMembers());

            // restart from scratch to acquire leadership
            this.serializedExecutor.execute(this::refreshStatus);
        }
    }

    private void rescheduleAfterDelay() {
        this.serializedExecutor.schedule(this::refreshStatus, jitter(this.lockConfiguration.getRetryPeriodMillis(), this.lockConfiguration.getJitterFactor()),
                TimeUnit.MILLISECONDS);
    }

    private boolean lookupNewLeaderInfo() {
        LOG.debug("{} Looking up leadership information...", logPrefix());

        ConfigMap configMap;
        try {
            configMap = pullConfigMap();
        } catch (Throwable e) {
            LOG.warn(logPrefix() + " Unable to retrieve the current ConfigMap " + this.lockConfiguration.getConfigMapName() + " from Kubernetes");
            LOG.debug(logPrefix() + " Exception thrown during ConfigMap lookup", e);
            return false;
        }

        Set<String> members;
        try {
            members = Objects.requireNonNull(pullClusterMembers(), "Retrieved a null set of members");
        } catch (Throwable e) {
            LOG.warn(logPrefix() + " Unable to retrieve the list of cluster members from Kubernetes");
            LOG.debug(logPrefix() + " Exception thrown during Pod list lookup", e);
            return false;
        }

        updateLatestLeaderInfo(configMap, members);
        return true;
    }

    private boolean tryAcquireLeadership() {
        LOG.debug("{} Trying to acquire the leadership...", logPrefix());

        ConfigMap configMap = this.latestConfigMap;
        Set<String> members = this.latestMembers;
        LeaderInfo latestLeaderInfo = this.latestLeaderInfo;

        if (latestLeaderInfo == null || members == null) {
            LOG.warn(logPrefix() + " Unexpected condition. Latest leader info or list of members is empty.");
            return false;
        } else if (!members.contains(this.lockConfiguration.getPodName())) {
            LOG.warn(logPrefix() + " The list of cluster members " + latestLeaderInfo.getMembers() + " does not contain the current Pod. Cannot acquire" + " leadership.");
            return false;
        }

        // Info we would set set in the configmap to become leaders
        LeaderInfo newLeaderInfo = new LeaderInfo(this.lockConfiguration.getGroupName(), this.lockConfiguration.getPodName(), new Date(), members);

        if (configMap == null) {
            // No ConfigMap created so far
            LOG.debug("{} Lock configmap is not present in the Kubernetes namespace. A new ConfigMap will be created", logPrefix());
            ConfigMap newConfigMap = ConfigMapLockUtils.createNewConfigMap(this.lockConfiguration.getConfigMapName(), newLeaderInfo);

            try {
                kubernetesClient.configMaps().inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient)).create(newConfigMap);

                LOG.debug("{} ConfigMap {} successfully created", logPrefix(), this.lockConfiguration.getConfigMapName());
                updateLatestLeaderInfo(newConfigMap, members);
                return true;
            } catch (Exception ex) {
                // Suppress exception
                LOG.warn(logPrefix()
                        + " Unable to create the ConfigMap, it may have been created by other cluster members concurrently. If the problem persists, check if the service account has "
                        + "the right " + "permissions to create it");
                LOG.debug(logPrefix() + " Exception while trying to create the ConfigMap", ex);
                return false;
            }
        } else {
            LOG.debug("{} Lock configmap already present in the Kubernetes namespace. Checking...", logPrefix());
            LeaderInfo leaderInfo = ConfigMapLockUtils.getLeaderInfo(configMap, members, this.lockConfiguration.getGroupName());

            boolean canAcquire = !leaderInfo.hasValidLeader();
            if (canAcquire) {
                // Try to be the new leader
                try {
                    ConfigMap updatedConfigMap = ConfigMapLockUtils.getConfigMapWithNewLeader(configMap, newLeaderInfo);
                    kubernetesClient.configMaps().inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                            .withName(this.lockConfiguration.getConfigMapName()).lockResourceVersion(configMap.getMetadata().getResourceVersion()).replace(updatedConfigMap);

                    LOG.debug("{} ConfigMap {} successfully updated", logPrefix(), this.lockConfiguration.getConfigMapName());
                    updateLatestLeaderInfo(updatedConfigMap, members);
                    return true;
                } catch (Exception ex) {
                    LOG.warn(logPrefix() + " Unable to update the lock ConfigMap to set leadership information");
                    LOG.debug(logPrefix() + " Error received during configmap lock replace", ex);
                    return false;
                }
            } else {
                // Another pod is the leader and it's still active
                LOG.debug("{} Another Pod ({}) is the current leader and it is still active", logPrefix(), this.latestLeaderInfo.getLeader());
                return false;
            }
        }
    }

    private void updateLatestLeaderInfo(ConfigMap configMap, Set<String> members) {
        LOG.debug("{} Updating internal status about the current leader", logPrefix());
        this.latestConfigMap = configMap;
        this.latestMembers = members;
        this.latestLeaderInfo = ConfigMapLockUtils.getLeaderInfo(configMap, members, this.lockConfiguration.getGroupName());
        LOG.debug("{} Current leader info: {}", logPrefix(), this.latestLeaderInfo);
    }

    private ConfigMap pullConfigMap() {
        return kubernetesClient.configMaps().inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withName(this.lockConfiguration.getConfigMapName()).get();
    }

    private Set<String> pullClusterMembers() {
        List<Pod> pods = kubernetesClient.pods().inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withLabels(this.lockConfiguration.getClusterLabels()).list().getItems();

        return pods.stream().map(pod -> pod.getMetadata().getName()).collect(Collectors.toSet());
    }

    private long jitter(long num, double factor) {
        return (long)(num * (1 + Math.random() * (factor - 1)));
    }

    private String logPrefix() {
        return "Pod[" + this.lockConfiguration.getPodName() + "]";
    }

}
