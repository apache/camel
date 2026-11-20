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
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.PodStatusUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors current status and participate to leader election when no active leaders are present. It communicates
 * changes in leadership and cluster members to the given event handler.
 */
public class KubernetesLeadershipController implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesLeadershipController.class);

    private enum State {
        NOT_LEADER,
        BECOMING_LEADER,
        LEADER,
        LOSING_LEADERSHIP,
        LEADERSHIP_LOST
    }

    private final CamelContext camelContext;

    private final KubernetesClient kubernetesClient;

    private final KubernetesLockConfiguration lockConfiguration;

    private final KubernetesClusterEventHandler eventHandler;

    // written by the refreshes, read by releaseLeadership once they have ended
    private volatile State currentState = State.NOT_LEADER;

    // set to null by stop, which a refresh running on the executor checks
    private volatile ScheduledExecutorService serializedExecutor;

    private TimedLeaderNotifier leaderNotifier;

    private final KubernetesLeaseResourceManager<HasMetadata> leaseManager;

    private volatile LeaderInfo latestLeaderInfo;
    private volatile HasMetadata latestLeaseResource;
    private volatile Set<String> latestMembers;

    private boolean disabled;

    private final String logPrefix;

    public KubernetesLeadershipController(CamelContext camelContext, KubernetesClient kubernetesClient,
                                          KubernetesLockConfiguration lockConfiguration,
                                          KubernetesClusterEventHandler eventHandler) {
        this.camelContext = camelContext;
        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandler = eventHandler;
        this.disabled = false;
        this.leaseManager = KubernetesLeaseResourceManager.create(lockConfiguration.getLeaseResourceType());

        logPrefix = "Pod[" + this.lockConfiguration.getPodName() + "]";
    }

    @Override
    public void start() {
        if (serializedExecutor == null) {
            LOG.debug("{} Starting leadership controller...", logPrefix);
            serializedExecutor = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "CamelKubernetesLeadershipController");
            leaderNotifier = new TimedLeaderNotifier(this.camelContext, this.eventHandler);

            leaderNotifier.start();
            serializedExecutor.execute(this::refreshStatus);
        }
    }

    /**
     * Stops the refreshes of the leadership status and the leadership events. A refresh that is running is interrupted,
     * and waited for up to the renew deadline. The lease is not released, see {@link #releaseLeadership()}.
     */
    @Override
    public void stop() {
        LOG.debug("{} Stopping leadership controller...", logPrefix);
        ScheduledExecutorService executor = serializedExecutor;
        serializedExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(lockConfiguration.getRenewDeadlineMillis(), TimeUnit.MILLISECONDS)) {
                    LOG.debug("{} The leadership status refresh did not end within the renew deadline", logPrefix);
                }
            } catch (InterruptedException e) {
                LOG.debug("{} Interrupted while waiting for the leadership status refresh to end", logPrefix);
                Thread.currentThread().interrupt();
            }
        }

        if (leaderNotifier != null) {
            leaderNotifier.stop();
        }
        leaderNotifier = null;
    }

    /**
     * Releases the leadership after {@link #stop()}: if the leadership of this pod has taken effect and the lease names
     * this pod, the pod is removed from it, so that another pod can take over the leadership at once instead of when
     * this pod is no longer ready. The release is given up after the renew deadline. A failure is logged, and the lease
     * is then kept until this pod is no longer ready (as before).
     */
    public void releaseLeadership() {
        String group = this.lockConfiguration.getGroupName();
        State state = this.currentState;
        if (state != State.LEADER && state != State.LOSING_LEADERSHIP && state != State.LEADERSHIP_LOST) {
            // when becoming leader, the pod waits for the previous leader to give up the leadership: an empty lease
            // would let another pod take it over with immediate effect while the previous leader may still act as one
            LOG.debug("{} The leadership for group {} has not taken effect ({}), the lease is not released", logPrefix,
                    group, state);
            return;
        }
        // the Kubernetes client tries a request again on its own when the API server cannot be reached or answers
        // with 429 or a server error (by default up to 10 times, with a growing delay), which can take minutes, so the
        // release runs on its own thread and the stop waits for it up to the renew deadline
        ExecutorService executor = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this,
                "CamelKubernetesLeadershipRelease");
        try {
            Future<?> release = executor.submit(this::doReleaseLeadership);
            release.get(this.lockConfiguration.getRenewDeadlineMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("{} Unable to release the leadership for group {} within the renew deadline, other pods can take it"
                     + " over once the current pod is no longer ready",
                    logPrefix, group);
        } catch (ExecutionException e) {
            LOG.warn("{} Unable to release the leadership for group {}: {}", logPrefix, group, e.getCause().toString());
            LOG.debug("{} Error received while releasing the leadership", logPrefix, e.getCause());
        } catch (InterruptedException e) {
            LOG.warn("{} Interrupted while releasing the leadership for group {}", logPrefix, group);
            Thread.currentThread().interrupt();
        } finally {
            camelContext.getExecutorServiceManager().shutdownNow(executor);
        }
    }

    private void doReleaseLeadership() {
        String group = this.lockConfiguration.getGroupName();
        // a conflict means that the lease was changed in the meantime (such as the renewal of another group in a
        // shared ConfigMap), so it is read again
        for (int attempt = 1; attempt <= 3; attempt++) {
            HasMetadata leaseResource;
            try {
                leaseResource = leaseManager.fetchLeaseResource(kubernetesClient,
                        this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient),
                        this.lockConfiguration.getKubernetesResourceName(), group);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    // the stop has given up the release (logged by releaseLeadership)
                    LOG.debug("{} Interrupted while retrieving the lease resource to release the leadership", logPrefix, e);
                    return;
                }
                LOG.warn("{} Unable to retrieve the lease resource {} for group {} to release the leadership: {}",
                        logPrefix, this.lockConfiguration.getKubernetesResourceName(), group, e.toString());
                LOG.debug("{} Exception thrown during lease resource lookup", logPrefix, e);
                return;
            }
            String leader = leaseResource != null
                    ? leaseManager.decodeLeaderInfo(leaseResource, Collections.emptySet(), group).getLeader() : null;
            if (!this.lockConfiguration.getPodName().equals(leader)) {
                LOG.debug("{} The lease for group {} does not name the current pod, nothing to release", logPrefix, group);
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                // the stop has given up the release (logged by releaseLeadership): do not update the lease afterwards
                LOG.debug("{} Interrupted before releasing the leadership", logPrefix);
                return;
            }
            try {
                leaseManager.optimisticDeleteLeaderInfo(kubernetesClient, leaseResource, group);
                LOG.info("{} Released the leadership for group {}", logPrefix, group);
                return;
            } catch (Exception e) {
                if (attempt < 3 && e instanceof KubernetesClientException kce && kce.getCode() == 409) {
                    LOG.debug("{} The lease resource for group {} was changed while releasing the leadership, trying again",
                            logPrefix, group);
                    continue;
                }
                if (Thread.currentThread().isInterrupted()) {
                    LOG.debug("{} Interrupted while releasing the leadership", logPrefix, e);
                    return;
                }
                LOG.warn("{} Unable to release the leadership for group {}, other pods can take it over once the current"
                         + " pod is no longer ready: {}",
                        logPrefix, group, e.toString());
                LOG.debug("{} Error received while releasing the leadership", logPrefix, e);
                return;
            }
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        boolean oldState = this.disabled;
        this.disabled = disabled;
        ScheduledExecutorService executor = serializedExecutor;
        if (oldState != disabled && executor != null) {
            try {
                executor.execute(this::refreshStatus);
            } catch (RejectedExecutionException e) {
                LOG.debug("{} Cannot refresh the leadership status as the controller is stopping", logPrefix);
            }
        }
    }

    private void refreshStatus() {
        try {
            doRefreshStatus();
        } catch (Exception e) {
            ScheduledExecutorService executor = this.serializedExecutor;
            if (executor == null || executor.isShutdown()) {
                LOG.debug("{} Exception thrown while refreshing the leadership status during stop", logPrefix, e);
                return;
            }
            // a failure must not end the refresh loop: the next run pulls the current state from the cluster again
            LOG.warn("{} Error while refreshing the leadership status (will try again): {}", logPrefix, e.getMessage());
            LOG.debug("{} Exception thrown while refreshing the leadership status", logPrefix, e);
            try {
                executor.schedule(this::refreshStatus,
                        jitter(this.lockConfiguration.getRetryPeriodMillis(), this.lockConfiguration.getJitterFactor()),
                        TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                LOG.debug("{} Cannot reschedule the leadership status refresh as the controller is stopping", logPrefix);
            }
        }
    }

    private void doRefreshStatus() {
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
            case LOSING_LEADERSHIP:
                refreshStatusLosingLeadership();
                break;
            case LEADERSHIP_LOST:
                refreshStatusLeadershipLost();
                break;
            default:
                throw new RuntimeCamelException("Unsupported state " + currentState);
        }
    }

    /**
     * This pod is currently not leader. It should monitor the leader configuration and try to acquire the leadership if
     * possible.
     */
    private void refreshStatusNotLeader() {
        LOG.debug("{} Pod is not leader, pulling new data from the cluster", logPrefix);
        boolean pulled = lookupNewLeaderInfo();
        if (!pulled) {
            rescheduleAfterDelay();
            return;
        }

        if (this.latestLeaderInfo.hasEmptyLeader()) {
            // There is no previous leader
            LOG.info("{} The cluster has no leaders for group {}. Trying to acquire the leadership...", logPrefix,
                    this.lockConfiguration.getGroupName());
            boolean acquired = tryAcquireLeadership();
            if (acquired) {
                LOG.info("{} Leadership acquired by current pod with immediate effect", logPrefix);
                this.currentState = State.LEADER;
                this.serializedExecutor.execute(this::refreshStatus);
                return;
            } else {
                LOG.info("{} Unable to acquire the leadership, it may have been acquired by another pod", logPrefix);
            }
        } else if (!this.latestLeaderInfo.hasValidLeader()) {
            // There's a previous leader and it's invalid
            LOG.info("{} Leadership has been lost by old owner. Trying to acquire the leadership...", logPrefix);
            boolean acquired = tryAcquireLeadership();
            if (acquired) {
                LOG.info("{} Leadership acquired by current pod", logPrefix);
                this.currentState = State.BECOMING_LEADER;
                this.serializedExecutor.execute(this::refreshStatus);
                return;
            } else {
                LOG.info("{} Unable to acquire the leadership, it may have been acquired by another pod", logPrefix);
            }
        } else if (this.latestLeaderInfo.isValidLeader(this.lockConfiguration.getPodName())) {
            // We are leaders for some reason (e.g. pod restart on failure)
            LOG.info("{} Leadership is already owned by current pod", logPrefix);
            this.currentState = State.BECOMING_LEADER;
            this.serializedExecutor.execute(this::refreshStatus);
            return;
        }

        this.leaderNotifier.refreshLeadership(Optional.ofNullable(this.latestLeaderInfo.getLeader()),
                System.currentTimeMillis(), this.lockConfiguration.getLeaseDurationMillis(),
                this.latestLeaderInfo.getMembers());
        rescheduleAfterDelay();
    }

    /**
     * This pod has acquired the leadership but it should wait for the old leader to tear down resources before starting
     * the local services.
     */
    private void refreshStatusBecomingLeader() {
        // Wait always the same amount of time before becoming the leader
        // Even if the current pod is already leader, we should let a possible
        // old version of the pod to shut down
        long delay = this.lockConfiguration.getLeaseDurationMillis();
        LOG.info("{} Current pod owns the leadership, but it will be effective in {} seconds...", logPrefix,
                new BigDecimal(delay).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // the controller is stopping (shutdownNow): the leadership has not taken effect, see releaseLeadership
            LOG.debug("{} Interrupted while waiting to become the leader", logPrefix);
            Thread.currentThread().interrupt();
            return;
        }

        LOG.info("{} Current pod is becoming the new leader now...", logPrefix);
        this.currentState = State.LEADER;
        this.serializedExecutor.execute(this::refreshStatus);
    }

    /**
     * This pod is going to manually lose the leadership. It should shutdown activities and wait a lease amount of time
     * before giving up the lease.
     */
    private void refreshStatusLosingLeadership() {
        // Wait always the same amount of time before giving up the leadership
        long delay = this.lockConfiguration.getLeaseDurationMillis();
        LOG.info("{} Current pod owns the leadership, but it will be lost in {} seconds...", logPrefix,
                new BigDecimal(delay).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            LOG.warn("Thread interrupted", e);
            Thread.currentThread().interrupt();
        }

        LOG.info("{} Current pod is losing leadership now...", logPrefix);
        this.currentState = State.LEADERSHIP_LOST;
        this.serializedExecutor.execute(this::refreshStatus);
    }

    /**
     * Functions are stopped, now lost leadership should be communicated by freeing up the lease.
     */
    private void refreshStatusLeadershipLost() {
        boolean pulled = lookupNewLeaderInfo();
        if (!pulled) {
            rescheduleAfterDelay();
            return;
        }

        if (!this.yieldLeadership()) {
            rescheduleAfterDelay();
            return;
        }

        LOG.info("{} Current pod has lost leadership", logPrefix);
        this.currentState = State.NOT_LEADER;
        this.serializedExecutor.execute(this::refreshStatus);
    }

    private void refreshStatusLeader() {
        if (this.disabled) {
            LOG.debug("{} Leadership disabled, pod is going to lose leadership", logPrefix);
            this.currentState = State.LOSING_LEADERSHIP;
            this.serializedExecutor.execute(this::refreshStatus);
            return;
        }

        LOG.debug("{} Pod should be the leader, pulling new data from the cluster", logPrefix);
        long timeBeforePulling = System.currentTimeMillis();
        boolean pulled = lookupNewLeaderInfo();
        if (!pulled) {
            rescheduleAfterDelay();
            return;
        }

        if (this.latestLeaderInfo.isValidLeader(this.lockConfiguration.getPodName())) {
            LOG.debug("{} Current Pod is still the leader", logPrefix);

            this.leaderNotifier.refreshLeadership(Optional.of(this.lockConfiguration.getPodName()), timeBeforePulling,
                    this.lockConfiguration.getRenewDeadlineMillis(),
                    this.latestLeaderInfo.getMembers());

            HasMetadata newLease = this.leaseManager.refreshLeaseRenewTime(kubernetesClient, this.latestLeaseResource,
                    this.lockConfiguration.getRenewDeadlineSeconds());
            updateLatestLeaderInfo(newLease, this.latestMembers);

            rescheduleAfterDelay();
            return;
        } else {
            LOG.debug("{} Current Pod has lost the leadership", logPrefix);
            this.currentState = State.NOT_LEADER;
            // set a empty leader to signal leadership loss
            this.leaderNotifier.refreshLeadership(Optional.empty(), System.currentTimeMillis(),
                    lockConfiguration.getLeaseDurationMillis(), this.latestLeaderInfo.getMembers());

            // restart from scratch to acquire leadership
            this.serializedExecutor.execute(this::refreshStatus);
        }
    }

    private void rescheduleAfterDelay() {
        this.serializedExecutor.schedule(this::refreshStatus,
                jitter(this.lockConfiguration.getRetryPeriodMillis(), this.lockConfiguration.getJitterFactor()),
                TimeUnit.MILLISECONDS);
    }

    private boolean lookupNewLeaderInfo() {
        LOG.debug("{} Looking up leadership information...", logPrefix);

        HasMetadata leaseResource;
        try {
            leaseResource = leaseManager.fetchLeaseResource(kubernetesClient,
                    this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient),
                    this.lockConfiguration.getKubernetesResourceName(),
                    this.lockConfiguration.getGroupName());
        } catch (Exception e) {
            LOG.warn("{} Unable to retrieve the current lease resource {} for group {} from Kubernetes",
                    logPrefix, this.lockConfiguration.getKubernetesResourceName(), this.lockConfiguration.getGroupName());
            LOG.debug("{} Exception thrown during lease resource lookup", logPrefix, e);
            return false;
        }

        Set<String> members;
        try {
            members = Objects.requireNonNull(pullClusterMembers(), "Retrieved a null set of members");
        } catch (Exception e) {
            LOG.warn("{} Unable to retrieve the list of cluster members from Kubernetes", logPrefix);
            LOG.debug("{} Exception thrown during Pod list lookup", logPrefix, e);
            return false;
        }

        updateLatestLeaderInfo(leaseResource, members);
        return true;
    }

    private boolean yieldLeadership() {
        LOG.debug("{} Trying to yield the leadership...", logPrefix);

        HasMetadata leaseResource = this.latestLeaseResource;
        Set<String> members = this.latestMembers;
        LeaderInfo latestLeaderInfo = this.latestLeaderInfo;

        if (latestLeaderInfo == null || members == null) {
            LOG.warn("{} Unexpected condition. Latest leader info or list of members is empty.", logPrefix);
            return false;
        } else if (!members.contains(this.lockConfiguration.getPodName())) {
            LOG.warn("{} The list of cluster members {} does not contain the current Pod. Cannot yield the leadership.",
                    logPrefix, latestLeaderInfo.getMembers());
            return false;
        }

        if (leaseResource == null) {
            // Already yielded
            return true;
        }

        LOG.debug("{} Lock lease resource already present in the Kubernetes namespace. Checking...", logPrefix);
        LeaderInfo leaderInfo = leaseManager.decodeLeaderInfo(leaseResource, members, this.lockConfiguration.getGroupName());
        if (!leaderInfo.isValidLeader(this.lockConfiguration.getPodName())) {
            // Already yielded
            return true;
        }

        try {
            HasMetadata updatedLeaseResource = leaseManager.optimisticDeleteLeaderInfo(kubernetesClient, leaseResource,
                    this.lockConfiguration.getGroupName());

            LOG.debug("{} Lease resource {} for group {} successfully updated", logPrefix,
                    this.lockConfiguration.getKubernetesResourceName(), this.lockConfiguration.getGroupName());
            updateLatestLeaderInfo(updatedLeaseResource, members);
            return true;
        } catch (Exception ex) {
            LOG.warn("{} Unable to update the lock on the lease resource to remove leadership information", logPrefix);
            LOG.debug("{} Error received during resource lock replace", logPrefix, ex);
            return false;
        }
    }

    private boolean tryAcquireLeadership() {
        if (this.disabled) {
            LOG.debug("{} Won't try to acquire the leadership because it's disabled...", logPrefix);
            return false;
        }
        if (this.serializedExecutor == null) {
            // stopping: the lease must not be taken after stop has released it
            LOG.debug("{} Won't try to acquire the leadership because the controller is stopping", logPrefix);
            return false;
        }

        LOG.debug("{} Trying to acquire the leadership...", logPrefix);

        HasMetadata leaseResource = this.latestLeaseResource;
        Set<String> members = this.latestMembers;
        LeaderInfo latestLeaderInfo = this.latestLeaderInfo;

        if (latestLeaderInfo == null || members == null) {
            LOG.warn("{} Unexpected condition. Latest leader info or list of members is empty.", logPrefix);
            return false;
        } else if (!members.contains(this.lockConfiguration.getPodName())) {
            LOG.warn("{} The list of cluster members {} does not contain the current Pod. Cannot acquire leadership.",
                    logPrefix, latestLeaderInfo.getMembers());
            return false;
        }

        // Info we would set set in the lease resource to become leaders
        LeaderInfo newLeaderInfo = new LeaderInfo(
                this.lockConfiguration.getGroupName(), this.lockConfiguration.getPodName(), new Date(), members,
                this.lockConfiguration.getLeaseDurationSeconds());

        if (leaseResource == null) {
            // No leaseResource created so far
            LOG.debug("{} Lock lease resource is not present in the Kubernetes namespace. A new lease resource will be created",
                    logPrefix);

            try {
                HasMetadata newLeaseResource = leaseManager.createNewLeaseResource(kubernetesClient,
                        this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient),
                        this.lockConfiguration.getKubernetesResourceName(),
                        newLeaderInfo);

                LOG.debug("{} Lease resource {} successfully created for group {}", logPrefix,
                        this.lockConfiguration.getKubernetesResourceName(), newLeaderInfo.getGroupName());
                updateLatestLeaderInfo(newLeaseResource, members);
                return true;
            } catch (Exception ex) {
                // Suppress exception
                LOG.warn("{} Unable to create the lease resource, it may have been created by other cluster members "
                         + "concurrently. If the problem persists, check if the service account has the right permissions"
                         + " to create it",
                        logPrefix);
                LOG.debug("{} Exception while trying to create the lease resource", logPrefix, ex);
                return false;
            }
        } else {
            LOG.debug("{} Lock lease resource already present in the Kubernetes namespace. Checking...", logPrefix);
            LeaderInfo leaderInfo
                    = leaseManager.decodeLeaderInfo(leaseResource, members, this.lockConfiguration.getGroupName());

            boolean canAcquire = !leaderInfo.hasValidLeader();
            if (canAcquire) {
                // Try to be the new leader
                try {
                    HasMetadata updatedLeaseResource
                            = leaseManager.optimisticAcquireLeadership(kubernetesClient, leaseResource, newLeaderInfo);

                    LOG.debug("{} Lease resource {} successfully updated for group {}", logPrefix,
                            this.lockConfiguration.getKubernetesResourceName(), newLeaderInfo.getGroupName());
                    updateLatestLeaderInfo(updatedLeaseResource, members);
                    return true;
                } catch (Exception ex) {
                    LOG.warn("{} Unable to update the lock lease resource to set leadership information", logPrefix);
                    LOG.debug("{} Error received during lease resource lock replace", logPrefix, ex);
                    return false;
                }
            } else {
                // Another pod is the leader and it's still active
                LOG.debug("{} Another Pod ({}) is the current leader and it is still active", logPrefix,
                        this.latestLeaderInfo.getLeader());
                return false;
            }
        }
    }

    private void updateLatestLeaderInfo(HasMetadata leaseResource, Set<String> members) {
        LOG.debug("{} Updating internal status about the current leader", logPrefix);
        this.latestLeaseResource = leaseResource;
        this.latestMembers = members;
        this.latestLeaderInfo = leaseManager.decodeLeaderInfo(leaseResource, members, this.lockConfiguration.getGroupName());
        LOG.debug("{} Current leader info: {}", logPrefix, this.latestLeaderInfo);
    }

    private Set<String> pullClusterMembers() {
        List<Pod> pods = kubernetesClient.pods()
                .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withLabels(this.lockConfiguration.getClusterLabels()).list().getItems();

        return pods.stream()
                .filter(PodStatusUtil::isRunning)
                .filter(Readiness::isPodReady)
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toSet());
    }

    private long jitter(long num, double factor) {
        return (long) (num * (1 + Math.random() * (factor - 1))); // NOSONAR
    }
}
