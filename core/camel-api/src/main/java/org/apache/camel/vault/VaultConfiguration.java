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

/**
 * Base configuration for access to Vaults.
 */
public class VaultConfiguration {

    private AwsVaultConfiguration aws;
    private GcpVaultConfiguration gcp;
    private AzureVaultConfiguration azure;
    private HashicorpVaultConfiguration hashicorp;
    private KubernetesVaultConfiguration kubernetes;
    private KubernetesConfigMapVaultConfiguration kubernetesConfigmaps;
    private IBMSecretsManagerVaultConfiguration ibmSecretsManager;
    private SpringCloudConfigConfiguration springConfig;

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

    public AwsVaultConfiguration getAwsVaultConfiguration() {
        return aws;
    }

    public void setAwsVaultConfiguration(AwsVaultConfiguration aws) {
        this.aws = aws;
    }

    public GcpVaultConfiguration getGcpVaultConfiguration() {
        return gcp;
    }

    public void setGcpVaultConfiguration(GcpVaultConfiguration gcp) {
        this.gcp = gcp;
    }

    public AzureVaultConfiguration getAzureVaultConfiguration() {
        return azure;
    }

    public void setAzureVaultConfiguration(AzureVaultConfiguration azure) {
        this.azure = azure;
    }

    public HashicorpVaultConfiguration getHashicorpVaultConfiguration() {
        return hashicorp;
    }

    public void setHashicorpVaultConfiguration(HashicorpVaultConfiguration hashicorp) {
        this.hashicorp = hashicorp;
    }

    public KubernetesVaultConfiguration getKubernetesVaultConfiguration() {
        return kubernetes;
    }

    public void setKubernetesVaultConfiguration(KubernetesVaultConfiguration kubernetes) {
        this.kubernetes = kubernetes;
    }

    public KubernetesConfigMapVaultConfiguration getKubernetesConfigMapVaultConfiguration() {
        return kubernetesConfigmaps;
    }

    public void setKubernetesConfigMapVaultConfiguration(KubernetesConfigMapVaultConfiguration kubernetesConfigmaps) {
        this.kubernetesConfigmaps = kubernetesConfigmaps;
    }

    public IBMSecretsManagerVaultConfiguration getIBMSecretsManagerVaultConfiguration() {
        return ibmSecretsManager;
    }

    public void setIBMSecretsManagerVaultConfiguration(IBMSecretsManagerVaultConfiguration ibmSecretsManager) {
        this.ibmSecretsManager = ibmSecretsManager;
    }

    public SpringCloudConfigConfiguration getSpringCloudConfigConfiguration() {
        return springConfig;
    }

    public void setSpringCloudConfigConfiguration(SpringCloudConfigConfiguration springCloudConfigConfiguration) {
        this.springConfig = springCloudConfigConfiguration;
    }
}
