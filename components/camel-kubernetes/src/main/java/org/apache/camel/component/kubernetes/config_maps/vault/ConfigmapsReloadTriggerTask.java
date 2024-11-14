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
package org.apache.camel.component.kubernetes.config_maps.vault;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.kubernetes.properties.ConfigMapPropertiesFunction;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PeriodicTask("kubernetes-configmaps-refresh")
public class ConfigmapsReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private CamelContext camelContext;
    @Metadata(defaultValue = "true")
    private boolean reloadEnabled = true;
    private String configmaps;
    private KubernetesClient kubernetesClient;
    private ConfigMapPropertiesFunction propertiesFunction;
    private volatile Instant startingTime;

    private static final Logger LOG = LoggerFactory.getLogger(ConfigmapsReloadTriggerTask.class);

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    /**
     * Whether Camel should be reloaded on Secrets updated
     */
    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }

    /**
     * Starting Time Kubernetes secrets watcher
     */
    public Instant getStartingTime() {
        return startingTime;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("configmap");
        if (pf instanceof ConfigMapPropertiesFunction) {
            propertiesFunction = (ConfigMapPropertiesFunction) pf;
            LOG.debug("Auto-detecting configmaps from properties-function: {}", pf.getName());
        }
        // specific secrets
        configmaps = camelContext.getVaultConfiguration().kubernetesConfigmaps().getConfigmaps();
        if (ObjectHelper.isEmpty(configmaps) && propertiesFunction == null) {
            throw new IllegalArgumentException("Configmaps must be configured on Kubernetes configmaps vault configuration");
        }

        kubernetesClient = propertiesFunction.getClient();
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (kubernetesClient != null) {
            try {
                kubernetesClient.close();
            } catch (Exception e) {
                // ignore
            }
            kubernetesClient = null;
        }
    }

    @Override
    public void run() {
        startingTime = Instant.now();
        final CountDownLatch isWatchClosed = new CountDownLatch(1);
        Watch watch = kubernetesClient.configMaps().inNamespace(kubernetesClient.getNamespace()).watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, ConfigMap configMap) {
                switch (action.name()) {
                    case "MODIFIED":
                        if (isReloadEnabled()) {
                            if (matchSecret(configMap.getMetadata().getName())) {
                                LOG.info("Update for Kubernetes Configmaps: {} detected, triggering CamelContext reload",
                                        configMap.getMetadata().getName());
                                ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
                                if (reload != null) {
                                    // trigger reload
                                    reload.onReload(this);
                                }
                            }
                        }
                        break;
                    default:
                        LOG.debug("Not watched event {}", action.name());
                }
            }

            @Override
            public void onClose(WatcherException e) {
                isWatchClosed.countDown();
            }
        });

        // Wait till watch gets closed
        try {
            isWatchClosed.await();
        } catch (InterruptedException e) {
            LOG.debug("Interrupted while waiting for the watch to close: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        watch.close();
    }

    protected boolean matchSecret(String name) {
        Set<String> set = new HashSet<>();
        if (configmaps != null) {
            Collections.addAll(set, configmaps.split(","));
        }

        for (String part : set) {
            boolean result = name.contains(part) || PatternHelper.matchPattern(name, part);
            LOG.trace("Matching configmap id: {}={} -> {}", name, part, result);
            if (result) {
                return true;
            }
        }

        return false;
    }
}
