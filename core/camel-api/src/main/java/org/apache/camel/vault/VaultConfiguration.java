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

import org.jspecify.annotations.Nullable;

/**
 * Base configuration for access to Vaults.
 */
public class VaultConfiguration {

    private @Nullable AwsVaultConfiguration aws;
    private @Nullable GcpVaultConfiguration gcp;
    private @Nullable AzureVaultConfiguration azure;
    private @Nullable HashicorpVaultConfiguration hashicorp;
    private @Nullable KubernetesVaultConfiguration kubernetes;
    private @Nullable KubernetesConfigMapVaultConfiguration kubernetesConfigmaps;
    private @Nullable IBMSecretsManagerVaultConfiguration ibmSecretsManager;
    private @Nullable SpringCloudConfigConfiguration springConfig;
    private @Nullable CyberArkVaultConfiguration cyberark;

    /**
     * AWS Vault Configuration
     */
    public AwsVaultConfiguration aws() {
        if (aws == null) {
            aws = new AwsVaultConfiguration();
        }
        return aws;
    }

    /**
     * GCP Vault Configuration
     */
    public GcpVaultConfiguration gcp() {
        if (gcp == null) {
            gcp = new GcpVaultConfiguration();
        }
        return gcp;
    }

    /**
     * Azure Vault Configuration
     */
    public AzureVaultConfiguration azure() {
        if (azure == null) {
            azure = new AzureVaultConfiguration();
        }
        return azure;
    }

    /**
     * Hashicorp Vault Configuration
     */
    public HashicorpVaultConfiguration hashicorp() {
        if (hashicorp == null) {
            hashicorp = new HashicorpVaultConfiguration();
        }
        return hashicorp;
    }

    /**
     * Kubernetes Vault Configuration
     */
    public KubernetesVaultConfiguration kubernetes() {
        if (kubernetes == null) {
            kubernetes = new KubernetesVaultConfiguration();
        }
        return kubernetes;
    }

    /**
     * Kubernetes Configmaps Vault Configuration
     */
    public KubernetesConfigMapVaultConfiguration kubernetesConfigmaps() {
        if (kubernetesConfigmaps == null) {
            kubernetesConfigmaps = new KubernetesConfigMapVaultConfiguration();
        }
        return kubernetesConfigmaps;
    }

    /**
     * IBM Secrets Manager Vault Configuration
     */
    public IBMSecretsManagerVaultConfiguration ibmSecretsManager() {
        if (ibmSecretsManager == null) {
            ibmSecretsManager = new IBMSecretsManagerVaultConfiguration();
        }
        return ibmSecretsManager;
    }

    public SpringCloudConfigConfiguration springConfig() {
        if (springConfig == null) {
            springConfig = new SpringCloudConfigConfiguration();
        }
        return springConfig;
    }

    /**
     * CyberArk Vault Configuration
     */
    public CyberArkVaultConfiguration cyberark() {
        if (cyberark == null) {
            cyberark = new CyberArkVaultConfiguration();
        }
        return cyberark;
    }

    public @Nullable AwsVaultConfiguration getAwsVaultConfiguration() {
        return aws;
    }

    public void setAwsVaultConfiguration(@Nullable AwsVaultConfiguration aws) {
        this.aws = aws;
    }

    public @Nullable GcpVaultConfiguration getGcpVaultConfiguration() {
        return gcp;
    }

    public void setGcpVaultConfiguration(@Nullable GcpVaultConfiguration gcp) {
        this.gcp = gcp;
    }

    public @Nullable AzureVaultConfiguration getAzureVaultConfiguration() {
        return azure;
    }

    public void setAzureVaultConfiguration(@Nullable AzureVaultConfiguration azure) {
        this.azure = azure;
    }

    public @Nullable HashicorpVaultConfiguration getHashicorpVaultConfiguration() {
        return hashicorp;
    }

    public void setHashicorpVaultConfiguration(@Nullable HashicorpVaultConfiguration hashicorp) {
        this.hashicorp = hashicorp;
    }

    public @Nullable KubernetesVaultConfiguration getKubernetesVaultConfiguration() {
        return kubernetes;
    }

    public void setKubernetesVaultConfiguration(@Nullable KubernetesVaultConfiguration kubernetes) {
        this.kubernetes = kubernetes;
    }

    public @Nullable KubernetesConfigMapVaultConfiguration getKubernetesConfigMapVaultConfiguration() {
        return kubernetesConfigmaps;
    }

    public void setKubernetesConfigMapVaultConfiguration(@Nullable KubernetesConfigMapVaultConfiguration kubernetesConfigmaps) {
        this.kubernetesConfigmaps = kubernetesConfigmaps;
    }

    public @Nullable IBMSecretsManagerVaultConfiguration getIBMSecretsManagerVaultConfiguration() {
        return ibmSecretsManager;
    }

    public void setIBMSecretsManagerVaultConfiguration(@Nullable IBMSecretsManagerVaultConfiguration ibmSecretsManager) {
        this.ibmSecretsManager = ibmSecretsManager;
    }

    public @Nullable SpringCloudConfigConfiguration getSpringCloudConfigConfiguration() {
        return springConfig;
    }

    public void setSpringCloudConfigConfiguration(@Nullable SpringCloudConfigConfiguration springCloudConfigConfiguration) {
        this.springConfig = springCloudConfigConfiguration;
    }

    public @Nullable CyberArkVaultConfiguration getCyberArkVaultConfiguration() {
        return cyberark;
    }

    public void setCyberArkVaultConfiguration(@Nullable CyberArkVaultConfiguration cyberark) {
        this.cyberark = cyberark;
    }
}
