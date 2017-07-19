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

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors current status and participate to leader election when no active leaders are present.
 * It communicates changes in leadership and cluster members to the given event handler.
 */
public class KubernetesLeaseBasedLeadershipController implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesLeaseBasedLeadershipController.class);

    private static final long FIXED_ADDITIONAL_DELAY = 100;

    private KubernetesClient kubernetesClient;

    private KubernetesLockConfiguration lockConfiguration;

    private KubernetesClusterEventHandler eventHandler;

    private ScheduledExecutorService serializedExecutor;
    private ScheduledExecutorService eventDispatcherExecutor;

    private KubernetesMembersMonitor membersMonitor;

    private Optional<String> currentLeader = Optional.empty();

    private volatile LeaderInfo latestLeaderInfo;

    public KubernetesLeaseBasedLeadershipController(KubernetesClient kubernetesClient, KubernetesLockConfiguration lockConfiguration, KubernetesClusterEventHandler eventHandler) {
        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandler = eventHandler;
    }

    @Override
    public void start() throws Exception {
        if (serializedExecutor == null) {
            LOG.debug("Starting leadership controller...");
            serializedExecutor = Executors.newSingleThreadScheduledExecutor();

            eventDispatcherExecutor = Executors.newSingleThreadScheduledExecutor();

            membersMonitor = new KubernetesMembersMonitor(this.serializedExecutor, this.kubernetesClient, this.lockConfiguration);
            if (eventHandler != null) {
                membersMonitor.addClusterEventHandler(eventHandler);
            }

            membersMonitor.start();
            serializedExecutor.execute(this::initialization);
        }
    }

    @Override
    public void stop() throws Exception {
        LOG.debug("Stopping leadership controller...");
        if (serializedExecutor != null) {
            serializedExecutor.shutdownNow();
        }
        if (eventDispatcherExecutor != null) {
            eventDispatcherExecutor.shutdown();
            eventDispatcherExecutor.awaitTermination(2, TimeUnit.SECONDS);
            eventDispatcherExecutor.shutdownNow();
        }
        if (membersMonitor != null) {
            membersMonitor.stop();
        }

        membersMonitor = null;
        eventDispatcherExecutor = null;
        serializedExecutor = null;
    }

    /**
     * Get the first ConfigMap and setup the initial state.
     */
    private void initialization() {
        LOG.debug("Reading (with retry) the configmap {} to detect the current leader", this.lockConfiguration.getConfigMapName());
        refreshConfigMapFromCluster(true);

        if (isCurrentPodTheActiveLeader()) {
            serializedExecutor.execute(this::onLeadershipAcquired);
        } else {
            LOG.info("The current pod ({}) is not the leader of the group '{}' in ConfigMap '{}' at this time", this.lockConfiguration.getPodName(), this.lockConfiguration
                    .getGroupName(), this.lockConfiguration.getConfigMapName());
            serializedExecutor.execute(this::acquireLeadershipCycle);
        }
    }

    /**
     * Signals the acquisition of the leadership and move to the keep-leadership state.
     */
    private void onLeadershipAcquired() {
        LOG.info("The current pod ({}) is now the leader of the group '{}' in ConfigMap '{}'", this.lockConfiguration.getPodName(), this.lockConfiguration
                .getGroupName(), this.lockConfiguration.getConfigMapName());

        this.eventDispatcherExecutor.execute(this::checkAndNotifyNewLeader);

        long nextDelay = computeNextRenewWaitTime(this.latestLeaderInfo.getTimestamp(), this.latestLeaderInfo.getTimestamp());
        serializedExecutor.schedule(this::keepLeadershipCycle, nextDelay + FIXED_ADDITIONAL_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * While in the keep-leadership state, the controller periodically renews the lease.
     * If a renewal deadline is met and it was not possible to renew the lease, the leadership is lost.
     */
    private void keepLeadershipCycle() {
        // renew lease periodically
        refreshConfigMapFromCluster(false); // if possible, update

        if (this.latestLeaderInfo.isTimeElapsedSeconds(lockConfiguration.getRenewDeadlineSeconds()) || !this.latestLeaderInfo.isLeader(this.lockConfiguration.getPodName())) {
            // Time over for renewal or leadership lost
            LOG.debug("The current pod ({}) has lost the leadership", this.lockConfiguration.getPodName());
            serializedExecutor.execute(this::onLeadershipLost);
            return;
        }

        boolean success = tryAcquireOrRenewLeadership();
        LOG.debug("Attempted to renew the lease. Success={}", success);

        long nextDelay = computeNextRenewWaitTime(this.latestLeaderInfo.getTimestamp(), new Date());
        serializedExecutor.schedule(this::keepLeadershipCycle, nextDelay + FIXED_ADDITIONAL_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Compute the timestamp of next event while in keep-leadership state.
     */
    private long computeNextRenewWaitTime(Date lastRenewal, Date lastRenewalAttempt) {
        long timeDeadline = lastRenewal.getTime() + this.lockConfiguration.getRenewDeadlineSeconds() * 1000;
        long timeRetry;
        long counter = 0;
        do {
            counter++;
            timeRetry = lastRenewal.getTime() + counter * this.lockConfiguration.getRetryPeriodSeconds() * 1000;
        } while (timeRetry < lastRenewalAttempt.getTime() && timeRetry < timeDeadline);

        long time = Math.min(timeRetry, timeDeadline);
        long delay = Math.max(0, time - System.currentTimeMillis());
        long delayJittered = jitter(delay, lockConfiguration.getJitterFactor());
        LOG.debug("Next renewal timeout event will be fired in {} seconds", delayJittered / 1000);
        return delayJittered;
    }


    /**
     * Signals the loss of leadership and move to the acquire-leadership state.
     */
    private void onLeadershipLost() {
        LOG.info("The local pod ({}) is no longer the leader of the group '{}' in ConfigMap '{}'", this.lockConfiguration.getPodName(), this.lockConfiguration.getGroupName(),
                this.lockConfiguration.getConfigMapName());

        this.eventDispatcherExecutor.execute(this::checkAndNotifyNewLeader);
        serializedExecutor.execute(this::acquireLeadershipCycle);
    }

    /**
     * While in the acquire-leadership state, the controller waits for the current lease to expire before trying to acquire the leadership.
     */
    private void acquireLeadershipCycle() {
        // wait for the current lease to finish then fire an election
        refreshConfigMapFromCluster(false); // if possible, update

        // Notify about changes in current leader if any
        this.eventDispatcherExecutor.execute(this::checkAndNotifyNewLeader);

        if (!this.latestLeaderInfo.isTimeElapsedSeconds(lockConfiguration.getLeaseDurationSeconds())) {
            // Wait for the lease to finish before trying leader election
            long nextDelay = computeNextElectionWaitTime(this.latestLeaderInfo.getTimestamp());
            serializedExecutor.schedule(this::acquireLeadershipCycle, nextDelay + FIXED_ADDITIONAL_DELAY, TimeUnit.MILLISECONDS);
            return;
        }

        boolean acquired = tryAcquireOrRenewLeadership();
        if (acquired) {
            LOG.debug("Leadership acquired for ConfigMap {}. Notification in progress...", this.lockConfiguration.getConfigMapName());
            serializedExecutor.execute(this::onLeadershipAcquired);
            return;
        }

        // Notify about changes in current leader if any
        this.eventDispatcherExecutor.execute(this::checkAndNotifyNewLeader);

        LOG.debug("Cannot acquire the leadership for ConfigMap {}", this.lockConfiguration.getConfigMapName());
        long nextDelay = computeNextElectionWaitTime(this.latestLeaderInfo.getTimestamp());
        serializedExecutor.schedule(this::acquireLeadershipCycle, nextDelay + FIXED_ADDITIONAL_DELAY, TimeUnit.MILLISECONDS);
    }

    private long computeNextElectionWaitTime(Date lastRenewal) {
        if (lastRenewal == null) {
            LOG.debug("Error detected while getting leadership info, next election timeout event will be fired in {} seconds", this.lockConfiguration.getRetryOnErrorIntervalSeconds());
            return this.lockConfiguration.getRetryOnErrorIntervalSeconds();
        }
        long time = lastRenewal.getTime() + this.lockConfiguration.getLeaseDurationSeconds() * 1000
                + jitter(this.lockConfiguration.getRetryPeriodSeconds() * 1000, this.lockConfiguration.getJitterFactor());

        long delay = Math.max(0, time - System.currentTimeMillis());
        LOG.debug("Next election timeout event will be fired in {} seconds", delay / 1000);
        return delay;
    }

    private long jitter(long num, double factor) {
        return (long) (num * (1 + Math.random() * (factor - 1)));
    }

    private boolean tryAcquireOrRenewLeadership() {
        LOG.debug("Trying to acquire or renew the leadership...");

        ConfigMap configMap;
        try {
            configMap = pullConfigMap();
        } catch (Exception e) {
            LOG.warn("Unable to retrieve the current ConfigMap " + this.lockConfiguration.getConfigMapName() + " from Kubernetes", e);
            return false;
        }

        // Info to set in the configmap to become leaders
        LeaderInfo newLeaderInfo = new LeaderInfo(this.lockConfiguration.getGroupName(), this.lockConfiguration.getPodName(), new Date());

        if (configMap == null) {
            // No configmap created so far
            LOG.debug("Lock configmap is not present in the Kubernetes namespace. A new ConfigMap will be created");
            ConfigMap newConfigMap = ConfigMapLockUtils.createNewConfigMap(this.lockConfiguration.getConfigMapName(), newLeaderInfo);

            try {
                kubernetesClient.configMaps()
                        .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                        .create(newConfigMap);
            } catch (Exception ex) {
                // Suppress exception
                LOG.warn("Unable to create the ConfigMap, it may have been created by other cluster members concurrently. If the problem persists, check if the service account has the right "
                        + "permissions to create it");
                LOG.debug("Exception while trying to create the ConfigMap", ex);

                // Try to get the configMap and return the current leader
                refreshConfigMapFromCluster(false);
                return isCurrentPodTheActiveLeader();
            }

            LOG.debug("ConfigMap {} successfully created and local pod is leader", this.lockConfiguration.getConfigMapName());
            updateLatestLeaderInfo(newConfigMap);
            scheduleCheckForPossibleLeadershipLoss();
            return true;
        } else {
            LOG.debug("Lock configmap already present in the Kubernetes namespace. Checking...");
            LeaderInfo leaderInfo = ConfigMapLockUtils.getLeaderInfo(configMap, this.lockConfiguration.getGroupName());

            boolean weWereLeader = leaderInfo.isLeader(this.lockConfiguration.getPodName());
            boolean leaseExpired = leaderInfo.isTimeElapsedSeconds(this.lockConfiguration.getLeaseDurationSeconds());

            if (weWereLeader || leaseExpired) {
                // Renew the lease or set the new leader
                try {
                    ConfigMap updatedConfigMap = ConfigMapLockUtils.getConfigMapWithNewLeader(configMap, newLeaderInfo);
                    kubernetesClient.configMaps()
                            .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                            .withName(this.lockConfiguration.getConfigMapName())
                            .lockResourceVersion(configMap.getMetadata().getResourceVersion())
                            .replace(updatedConfigMap);

                    LOG.debug("ConfigMap {} successfully updated and local pod is leader", this.lockConfiguration.getConfigMapName());
                    updateLatestLeaderInfo(updatedConfigMap);
                    scheduleCheckForPossibleLeadershipLoss();
                    return true;
                } catch (Exception ex) {
                    LOG.warn("An attempt to become leader has failed. It's possible that the leadership has been taken by another pod");
                    LOG.debug("Error received during configmap lock replace", ex);

                    // Try to get the configMap and return the current leader
                    refreshConfigMapFromCluster(false);
                    return isCurrentPodTheActiveLeader();
                }
            } else {
                // Another pod is the leader and lease is not expired
                LOG.debug("Another pod is the current leader and lease has not expired yet");
                updateLatestLeaderInfo(configMap);
                return false;
            }
        }
    }


    private void refreshConfigMapFromCluster(boolean retry) {
        LOG.debug("Retrieving configmap {}", this.lockConfiguration.getConfigMapName());
        try {
            updateLatestLeaderInfo(pullConfigMap());
        } catch (Exception ex) {
            if (retry) {
                LOG.warn("ConfigMap pull failed. Retrying in " + this.lockConfiguration.getRetryOnErrorIntervalSeconds() + " seconds...", ex);
                try {
                    Thread.sleep(this.lockConfiguration.getRetryOnErrorIntervalSeconds() * 1000);
                    refreshConfigMapFromCluster(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Controller Thread interrupted, shutdown in progress", e);
                }
            } else {
                LOG.warn("Cannot retrieve the ConfigMap: pull failed", ex);
            }
        }
    }

    private boolean isCurrentPodTheActiveLeader() {
        return latestLeaderInfo != null
                && latestLeaderInfo.isLeader(this.lockConfiguration.getPodName())
                && !latestLeaderInfo.isTimeElapsedSeconds(this.lockConfiguration.getRenewDeadlineSeconds());
    }

    private ConfigMap pullConfigMap() {
        return kubernetesClient.configMaps()
                .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withName(this.lockConfiguration.getConfigMapName())
                .get();
    }


    private void updateLatestLeaderInfo(ConfigMap configMap) {
        LOG.debug("Updating internal status about the current leader");
        this.latestLeaderInfo = ConfigMapLockUtils.getLeaderInfo(configMap, this.lockConfiguration.getGroupName());
    }

    private void scheduleCheckForPossibleLeadershipLoss() {
        // Adding check for the case of main thread busy on http calls
        if (this.latestLeaderInfo.isLeader(this.lockConfiguration.getPodName())) {
            this.eventDispatcherExecutor.schedule(this::checkAndNotifyNewLeader, this.lockConfiguration.getRenewDeadlineSeconds() * 1000 + FIXED_ADDITIONAL_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private void checkAndNotifyNewLeader() {
        LOG.debug("Checking if the current leader has changed to notify the event handler...");
        LeaderInfo newLeaderInfo = this.latestLeaderInfo;
        if (newLeaderInfo == null) {
            return;
        }

        long leaderInfoDurationSeconds = newLeaderInfo.isLeader(this.lockConfiguration.getPodName())
                ? this.lockConfiguration.getRenewDeadlineSeconds()
                : this.lockConfiguration.getLeaseDurationSeconds();

        Optional<String> newLeader;
        if (newLeaderInfo.getLeader() != null && !newLeaderInfo.isTimeElapsedSeconds(leaderInfoDurationSeconds)) {
            newLeader = Optional.of(newLeaderInfo.getLeader());
        } else {
            newLeader = Optional.empty();
        }

        // Sending notifications in case of leader change
        if (!newLeader.equals(this.currentLeader)) {
            LOG.info("Current leader has changed from {} to {}. Sending notification...", this.currentLeader, newLeader);
            this.currentLeader = newLeader;
            eventHandler.onKubernetesClusterEvent((KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent) () -> newLeader);
        } else {
            LOG.debug("Current leader unchanged: {}", this.currentLeader);
        }
    }


}
