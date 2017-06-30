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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors continuously the configmap to detect changes in leadership.
 * It calls the callback eventHandlers only when the leader changes w.r.t. the previous invocation.
 */
class KubernetesLeaderMonitor implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesLeaderMonitor.class);

    private ScheduledExecutorService serializedExecutor;

    private KubernetesClient kubernetesClient;

    private KubernetesLockConfiguration lockConfiguration;

    private List<KubernetesClusterEventHandler> eventHandlers;

    private Watch watch;

    private boolean terminated;

    private boolean refreshing;

    private ConfigMap latestConfigMap;

    public KubernetesLeaderMonitor(ScheduledExecutorService serializedExecutor, KubernetesClient kubernetesClient, KubernetesLockConfiguration lockConfiguration) {
        this.serializedExecutor = serializedExecutor;
        this.kubernetesClient = kubernetesClient;
        this.lockConfiguration = lockConfiguration;
        this.eventHandlers = new LinkedList<>();
    }

    public void addClusterEventHandler(KubernetesClusterEventHandler leaderEventHandler) {
        this.eventHandlers.add(leaderEventHandler);
    }

    @Override
    public void start() throws Exception {
        this.terminated = false;
        serializedExecutor.execute(this::startWatch);
        serializedExecutor.execute(() -> doPoll(true));

        long recreationDelay = lockConfiguration.getWatchRefreshIntervalSecondsOrDefault();
        if (recreationDelay > 0) {
            serializedExecutor.scheduleWithFixedDelay(this::refresh, recreationDelay, recreationDelay, TimeUnit.SECONDS);
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

    private void startWatch() {
        try {
            LOG.debug("Starting ConfigMap watcher for monitoring the current leader");
            this.watch = kubernetesClient.configMaps()
                    .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                    .withName(this.lockConfiguration.getConfigMapName())
                    .watch(new Watcher<ConfigMap>() {

                        @Override
                        public void eventReceived(Action action, ConfigMap configMap) {
                            switch (action) {
                            case MODIFIED:
                            case DELETED:
                            case ADDED:
                                LOG.debug("Received update from watch on ConfigMap {}", configMap);
                                serializedExecutor.execute(() -> checkAndNotify(configMap));
                                break;
                            default:
                            }
                        }

                        @Override
                        public void onClose(KubernetesClientException e) {
                            if (!terminated) {
                                KubernetesLeaderMonitor.this.watch = null;
                                if (refreshing) {
                                    LOG.info("Refreshing ConfigMap watcher...");
                                    serializedExecutor.execute(KubernetesLeaderMonitor.this::startWatch);
                                } else {
                                    LOG.warn("ConfigMap watcher has been closed unexpectedly. Recreating it in 1 second...", e);
                                    serializedExecutor.schedule(KubernetesLeaderMonitor.this::startWatch, 1, TimeUnit.SECONDS);
                                }
                            }
                        }
                    });
        } catch (Exception ex) {
            LOG.warn("Unable to watch for configmap changes. Retrying in 5 seconds...");
            LOG.debug("Error while trying to watch the configmap", ex);

            this.serializedExecutor.schedule(this::startWatch, 5, TimeUnit.SECONDS);
        }
    }

    private void doPoll(boolean retry) {
        LOG.debug("Starting poll to get configmap {}", this.lockConfiguration.getConfigMapName());
        ConfigMap configMap;
        try {
            configMap = pollConfigMap();
        } catch (Exception ex) {
            if (retry) {
                LOG.warn("ConfigMap poll failed. Retrying in 5 seconds...", ex);
                this.serializedExecutor.schedule(() -> doPoll(true), 5, TimeUnit.SECONDS);
            } else {
                LOG.warn("ConfigMap poll failed", ex);
            }
            return;
        }

        checkAndNotify(configMap);
    }

    private void checkAndNotify(ConfigMap candidateConfigMap) {
        LOG.debug("Checking configMap {}", candidateConfigMap);
        ConfigMap newConfigMap = newest(this.latestConfigMap, candidateConfigMap);
        Optional<String> leader = extractLeader(newConfigMap);
        Optional<String> oldLeader = extractLeader(this.latestConfigMap);

        this.latestConfigMap = newConfigMap;

        LOG.debug("The new leader is {}. Old leader was {}", leader, oldLeader);
        if (!leader.equals(oldLeader)) {
            LOG.debug("Notifying the new leader to all eventHandlers");
            for (KubernetesClusterEventHandler eventHandler : eventHandlers) {
                eventHandler.onKubernetesClusterEvent((KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent) () -> leader);
            }
        } else {
            LOG.debug("Leader has not changed");
        }
    }

    private ConfigMap pollConfigMap() {
        return kubernetesClient.configMaps()
                .inNamespace(this.lockConfiguration.getKubernetesResourcesNamespaceOrDefault(kubernetesClient))
                .withName(this.lockConfiguration.getConfigMapName())
                .get();
    }

    private Optional<String> extractLeader(ConfigMap configMap) {
        Optional<String> leader = Optional.empty();
        if (configMap != null && configMap.getData() != null) {
            leader = Optional.ofNullable(configMap.getData().get(this.lockConfiguration.getGroupName()));
        }
        return leader;
    }

    private ConfigMap newest(ConfigMap configMap1, ConfigMap configMap2) {
        ConfigMap newest = null;

        if (configMap1 != null && configMap2 == null) {
            newest = configMap1;
        } else if (configMap1 == null && configMap2 != null) {
            newest = configMap2;
        }

        if (newest == null) {
            String rv1 = extractResourceVersion(configMap1);
            String rv2 = extractResourceVersion(configMap2);
            newest = newest(configMap1, configMap2, rv1, rv2);
        }

        if (newest == null) {
            String ct1 = extractCreationTimestamp(configMap1);
            String ct2 = extractCreationTimestamp(configMap2);
            // timestamps are string-comparable
            newest = newest(configMap1, configMap2, ct1, ct2);
        }

        return newest;
    }

    private <T extends Comparable<T>> ConfigMap newest(ConfigMap configMap1, ConfigMap configMap2, T cmp1, T cmp2) {
        if (cmp1 != null && cmp2 != null) {
            int comp = cmp1.compareTo(cmp2);
            if (comp > 0) {
                return configMap1;
            } else {
                return configMap2;
            }
        }
        return null;
    }

    private String extractResourceVersion(ConfigMap configMap) {
        if (configMap != null && configMap.getMetadata() != null) {
            return configMap.getMetadata().getResourceVersion();
        }
        return null;
    }

    private String extractCreationTimestamp(ConfigMap configMap) {
        if (configMap != null && configMap.getMetadata() != null) {
            return configMap.getMetadata().getCreationTimestamp();
        }
        return null;
    }

}
