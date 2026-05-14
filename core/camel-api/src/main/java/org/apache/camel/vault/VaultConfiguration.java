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
 *
 * @since 3.16
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

    /**
     * Spring Cloud Config Vault Configuration
     */
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

    /** Gets the AWS Vault configuration. */
    public @Nullable AwsVaultConfiguration getAwsVaultConfiguration() {
        return aws;
    }

    /** Sets the AWS Vault configuration. */
    public void setAwsVaultConfiguration(@Nullable AwsVaultConfiguration aws) {
        this.aws = aws;
    }

    /** Gets the GCP Vault configuration. */
    public @Nullable GcpVaultConfiguration getGcpVaultConfiguration() {
        return gcp;
    }

    /** Sets the GCP Vault configuration. */
    public void setGcpVaultConfiguration(@Nullable GcpVaultConfiguration gcp) {
        this.gcp = gcp;
    }

    /** Gets the Azure Vault configuration. */
    public @Nullable AzureVaultConfiguration getAzureVaultConfiguration() {
        return azure;
    }

    /** Sets the Azure Vault configuration. */
    public void setAzureVaultConfiguration(@Nullable AzureVaultConfiguration azure) {
        this.azure = azure;
    }

    /** Gets the Hashicorp Vault configuration. */
    public @Nullable HashicorpVaultConfiguration getHashicorpVaultConfiguration() {
        return hashicorp;
    }

    /** Sets the Hashicorp Vault configuration. */
    public void setHashicorpVaultConfiguration(@Nullable HashicorpVaultConfiguration hashicorp) {
        this.hashicorp = hashicorp;
    }

    /** Gets the Kubernetes Vault configuration. */
    public @Nullable KubernetesVaultConfiguration getKubernetesVaultConfiguration() {
        return kubernetes;
    }

    /** Sets the Kubernetes Vault configuration. */
    public void setKubernetesVaultConfiguration(@Nullable KubernetesVaultConfiguration kubernetes) {
        this.kubernetes = kubernetes;
    }

    /** Gets the Kubernetes ConfigMap Vault configuration. */
    public @Nullable KubernetesConfigMapVaultConfiguration getKubernetesConfigMapVaultConfiguration() {
        return kubernetesConfigmaps;
    }

    /** Sets the Kubernetes ConfigMap Vault configuration. */
    public void setKubernetesConfigMapVaultConfiguration(@Nullable KubernetesConfigMapVaultConfiguration kubernetesConfigmaps) {
        this.kubernetesConfigmaps = kubernetesConfigmaps;
    }

    /** Gets the IBM Secrets Manager Vault configuration. */
    public @Nullable IBMSecretsManagerVaultConfiguration getIBMSecretsManagerVaultConfiguration() {
        return ibmSecretsManager;
    }

    /** Sets the IBM Secrets Manager Vault configuration. */
    public void setIBMSecretsManagerVaultConfiguration(@Nullable IBMSecretsManagerVaultConfiguration ibmSecretsManager) {
        this.ibmSecretsManager = ibmSecretsManager;
    }

    /** Gets the Spring Cloud Config configuration. */
    public @Nullable SpringCloudConfigConfiguration getSpringCloudConfigConfiguration() {
        return springConfig;
    }

    /** Sets the Spring Cloud Config configuration. */
    public void setSpringCloudConfigConfiguration(@Nullable SpringCloudConfigConfiguration springCloudConfigConfiguration) {
        this.springConfig = springCloudConfigConfiguration;
    }

    /** Gets the CyberArk Vault configuration. */
    public @Nullable CyberArkVaultConfiguration getCyberArkVaultConfiguration() {
        return cyberark;
    }

    /** Sets the CyberArk Vault configuration. */
    public void setCyberArkVaultConfiguration(@Nullable CyberArkVaultConfiguration cyberark) {
        this.cyberark = cyberark;
    }
}
