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
package org.apache.camel.vault;

import org.apache.camel.spi.Metadata;

/**
 * Configuration for access to Kubernetes Secrets
 */
public class KubernetesVaultConfiguration extends VaultConfiguration {

    @Metadata(defaultValue = "true")
    private boolean refreshEnabled = true;
    @Metadata
    private String secrets;

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Kubernetes Cluster.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public String getSecrets() {
        return secrets;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public void setSecrets(String secrets) {
        this.secrets = secrets;
    }
}
