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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors the list of participants in a leader election and provides the most recently updated list.
 * It calls the callback eventHandlers only when the member set changes w.r.t. the previous invocation.
 */
class KubernetesMembersMonitor implements Service {

    private static final long DEFAULT_WATCHER_REFRESH_INTERVAL_SECONDS = 1800;

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesMembersMonitor.class);

    private ScheduledExecutorService serializedExecutor;

    private KubernetesClient kubernetesClient;

    private KubernetesLockConfiguration lockConfiguration;

    private List<KubernetesClusterEventHandler> eventHandlers;

    private Watch watch;

    private boolean terminated;

    private boolean refreshing;

    private Set<String> previousMembers = new HashSet<>();

    private Set<String> basePoll = new HashSet<>();
    private Set<String> deleted = new HashSet<>();
    private Set<String> added = new HashSet<>();

    public KubernetesMembersMonitor(ScheduledExecutorService serializedExecutor, KubernetesClient kubernetesClient, KubernetesLockConfiguration lockConfiguration) {
        this.serializedExecutor = serializedExecutor;
        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandlers = new LinkedList<>();
    }

    public void addClusterEventHandler(KubernetesClusterEventHandler eventHandler) {
        this.eventHandlers.add(eventHandler);
    }

    @Override
    public void start() throws Exception {
        serializedExecutor.execute(() -> doPoll(true));
        serializedExecutor.execute(this::createWatch);

        long recreationDelay = lockConfiguration.getWatchRefreshIntervalSecondsOrDefault();
        if (recreationDelay > 0) {
            serializedExecutor.scheduleWithFixedDelay(this::refresh, recreationDelay, recreationDelay, TimeUnit.SECONDS);
        }
    }

    private void createWatch() {
        try {
            LOG.debug("Starting cluster members watcher");
            this.watch = kubernetesClient.pods()
                    .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                    .withLabels(this.lockConfiguration.getClusterLabels())
                    .watch(new Watcher<Pod>() {

                        @Override
                        public void eventReceived(Action action, Pod pod) {
                            switch (action) {
                            case DELETED:
                                serializedExecutor.execute(() -> deleteAndNotify(podName(pod)));
                                break;
                            case ADDED:
                                serializedExecutor.execute(() -> addAndNotify(podName(pod)));
                                break;
                            default:
                            }
                        }

                        @Override
                        public void onClose(KubernetesClientException e) {
                            if (!terminated) {
                                KubernetesMembersMonitor.this.watch = null;
                                if (refreshing) {
                                    LOG.info("Refreshing pod list watcher...");
                                    serializedExecutor.execute(KubernetesMembersMonitor.this::createWatch);
                                } else {
                                    LOG.warn("Pod list watcher has been closed unexpectedly. Recreating it in 1 second...", e);
                                    serializedExecutor.schedule(KubernetesMembersMonitor.this::createWatch, 1, TimeUnit.SECONDS);
                                }
                            }
                        }
                    });
        } catch (Exception ex) {
            LOG.warn("Unable to watch for pod list changes. Retrying in 5 seconds...");
            LOG.debug("Error while trying to watch the pod list", ex);

            serializedExecutor.schedule(this::createWatch, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() throws Exception {
        this.terminated = true;
        Watch watch = this.watch;
        if (watch != null) {
            watch.close();
        }
    }

    public void refresh() {
        serializedExecutor.execute(() -> {
            if (!terminated) {
                refreshing = true;
                try {
                    doPoll(false);

                    Watch w = this.watch;
                    if (w != null) {
                        // It will be recreated
                        w.close();
                    }
                } finally {
                    refreshing = false;
                }
            }
        });
    }

    private void doPoll(boolean retry) {
        LOG.debug("Starting poll to get all cluster members");
        List<Pod> pods;
        try {
            pods = pollPods();
        } catch (Exception ex) {
            if (retry) {
                LOG.warn("Pods poll failed. Retrying in 5 seconds...", ex);
                this.serializedExecutor.schedule(() -> doPoll(true), 5, TimeUnit.SECONDS);
            } else {
                LOG.warn("Pods poll failed", ex);
            }
            return;
        }

        this.basePoll = pods.stream()
                .map(p -> Optional.ofNullable(podName(p)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        this.added = new HashSet<>();
        this.deleted = new HashSet<>();

        LOG.debug("Base list of members is {}", this.basePoll);

        checkAndNotify();
    }

    private List<Pod> pollPods() {
        return kubernetesClient.pods()
                .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withLabels(this.lockConfiguration.getClusterLabels())
                .list().getItems();
    }

    private String podName(Pod pod) {
        if (pod != null && pod.getMetadata() != null) {
            return pod.getMetadata().getName();
        }
        return null;
    }

    private void checkAndNotify() {
        Set<String> newMembers = new HashSet<>(basePoll);
        newMembers.addAll(added);
        newMembers.removeAll(deleted);

        LOG.debug("Current list of members is: {}", newMembers);

        if (!newMembers.equals(this.previousMembers)) {
            LOG.debug("List of members changed: sending notifications");
            this.previousMembers = newMembers;

            for (KubernetesClusterEventHandler eventHandler : eventHandlers) {
                eventHandler.onKubernetesClusterEvent((KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent) () -> newMembers);
            }
        } else {
            LOG.debug("List of members has not changed");
        }
    }

    private void addAndNotify(String member) {
        LOG.debug("Adding new member to the list: {}", member);
        if (member != null) {
            this.added.add(member);
            checkAndNotify();
        }
    }

    private void deleteAndNotify(String member) {
        LOG.debug("Deleting member to the list: {}", member);
        if (member != null) {
            this.deleted.add(member);
            checkAndNotify();
        }
    }

}
