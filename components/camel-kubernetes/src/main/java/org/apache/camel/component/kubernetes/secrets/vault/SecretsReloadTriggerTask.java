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
package org.apache.camel.component.kubernetes.secrets.vault;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.*;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.kubernetes.properties.SecretPropertiesFunction;
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

@PeriodicTask("kubernetes-secret-refresh")
public class SecretsReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private CamelContext camelContext;
    @Metadata(defaultValue = "true")
    private boolean reloadEnabled = true;
    private String secrets;
    private KubernetesClient kubernetesClient;
    private SecretPropertiesFunction propertiesFunction;
    private volatile Instant startingTime;

    private static final Logger LOG = LoggerFactory.getLogger(SecretsReloadTriggerTask.class);

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
        PropertiesFunction pf = pc.getPropertiesFunction("secret");
        if (pf instanceof SecretPropertiesFunction) {
            propertiesFunction = (SecretPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        secrets = camelContext.getVaultConfiguration().kubernetes().getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on Kubernetes vault configuration");
        }

        kubernetesClient = propertiesFunction.getClient();
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (kubernetesClient != null && !propertiesFunction.isAutowiredClient()) {
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
        Watch watch = kubernetesClient.secrets().inNamespace(kubernetesClient.getNamespace()).watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Secret secret) {
                switch (action.name()) {
                    case "MODIFIED":
                        if (isReloadEnabled()) {
                            if (matchSecret(secret.getMetadata().getName())) {
                                LOG.info("Update for Kubernetes Secret: {} detected, triggering CamelContext reload",
                                        secret.getMetadata().getName());
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
        if (secrets != null) {
            Collections.addAll(set, secrets.split(","));
        }

        for (String part : set) {
            boolean result = name.contains(part) || PatternHelper.matchPattern(name, part);
            LOG.trace("Matching secret id: {}={} -> {}", name, part, result);
            if (result) {
                return true;
            }
        }

        return false;
    }
}
