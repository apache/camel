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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.vault.KubernetesConfigMapVaultConfiguration;

/**
 * Configuration for access to Kubernetes Configmaps.
 */
@Configurer(extended = true)
public class KubernetesConfigmapsVaultConfigurationProperties extends KubernetesConfigMapVaultConfiguration
        implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public KubernetesConfigmapsVaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    /**
     * Whether to automatically reload Camel upon Configmaps being updated in Kubernetes Cluster.
     */
    public KubernetesConfigmapsVaultConfigurationProperties withRefreshEnabled(boolean refreshEnabled) {
        setRefreshEnabled(refreshEnabled);
        return this;
    }

    /**
     * Specify the configmaps names (or pattern) to check for updates. Multiple configmaps can be separated by comma.
     */
    public KubernetesConfigmapsVaultConfigurationProperties withConfigmaps(String configmaps) {
        setConfigmaps(configmaps);
        return this;
    }

}
