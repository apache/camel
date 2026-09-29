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
import org.apache.camel.vault.AwsVaultConfiguration;
import org.apache.camel.vault.AzureVaultConfiguration;
import org.apache.camel.vault.CyberArkVaultConfiguration;
import org.apache.camel.vault.GcpVaultConfiguration;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;
import org.apache.camel.vault.KubernetesConfigMapVaultConfiguration;
import org.apache.camel.vault.KubernetesVaultConfiguration;
import org.apache.camel.vault.SpringCloudConfigConfiguration;
import org.apache.camel.vault.VaultConfiguration;

public class VaultConfigurationProperties extends VaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;
    private AwsVaultConfigurationProperties aws;
    private GcpVaultConfigurationProperties gcp;
    private AzureVaultConfigurationProperties azure;
    private HashicorpVaultConfigurationProperties hashicorp;
    private KubernetesVaultConfigurationProperties kubernetes;
    private KubernetesConfigmapsVaultConfigurationProperties kubernetesConfigmaps;
    private IBMSecretsManagerVaultConfigurationProperties ibmSecretsManager;
    private SpringCloudConfigConfigurationProperties springConfig;
    private CyberArkVaultConfigurationProperties cyberark;

    public VaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
        if (aws != null) {
            aws.close();
        }
        if (gcp != null) {
            gcp.close();
        }
        if (azure != null) {
            azure.close();
        }
        if (hashicorp != null) {
            hashicorp.close();
        }
        if (kubernetes != null) {
            kubernetes.close();
        }
        if (kubernetesConfigmaps != null) {
            kubernetesConfigmaps.close();
        }
        if (ibmSecretsManager != null) {
            ibmSecretsManager.close();
        }
        if (springConfig != null) {
            springConfig.close();
        }
        if (cyberark != null) {
            cyberark.close();
        }
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class, but must use the configurations of the fluent builders

    @Override
    public AwsVaultConfiguration getAwsVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return aws != null ? aws : super.getAwsVaultConfiguration();
    }

    @Override
    public void setAwsVaultConfiguration(AwsVaultConfiguration aws) {
        super.setAwsVaultConfiguration(aws);
        this.aws = aws instanceof AwsVaultConfigurationProperties p ? p : null;
    }

    @Override
    public GcpVaultConfiguration getGcpVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return gcp != null ? gcp : super.getGcpVaultConfiguration();
    }

    @Override
    public void setGcpVaultConfiguration(GcpVaultConfiguration gcp) {
        super.setGcpVaultConfiguration(gcp);
        this.gcp = gcp instanceof GcpVaultConfigurationProperties p ? p : null;
    }

    @Override
    public AzureVaultConfiguration getAzureVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return azure != null ? azure : super.getAzureVaultConfiguration();
    }

    @Override
    public void setAzureVaultConfiguration(AzureVaultConfiguration azure) {
        super.setAzureVaultConfiguration(azure);
        this.azure = azure instanceof AzureVaultConfigurationProperties p ? p : null;
    }

    @Override
    public HashicorpVaultConfiguration getHashicorpVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return hashicorp != null ? hashicorp : super.getHashicorpVaultConfiguration();
    }

    @Override
    public void setHashicorpVaultConfiguration(HashicorpVaultConfiguration hashicorp) {
        super.setHashicorpVaultConfiguration(hashicorp);
        this.hashicorp = hashicorp instanceof HashicorpVaultConfigurationProperties p ? p : null;
    }

    @Override
    public KubernetesVaultConfiguration getKubernetesVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return kubernetes != null ? kubernetes : super.getKubernetesVaultConfiguration();
    }

    @Override
    public void setKubernetesVaultConfiguration(KubernetesVaultConfiguration kubernetes) {
        super.setKubernetesVaultConfiguration(kubernetes);
        this.kubernetes = kubernetes instanceof KubernetesVaultConfigurationProperties p ? p : null;
    }

    @Override
    public KubernetesConfigMapVaultConfiguration getKubernetesConfigMapVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return kubernetesConfigmaps != null ? kubernetesConfigmaps : super.getKubernetesConfigMapVaultConfiguration();
    }

    @Override
    public void setKubernetesConfigMapVaultConfiguration(KubernetesConfigMapVaultConfiguration kubernetesConfigmaps) {
        super.setKubernetesConfigMapVaultConfiguration(kubernetesConfigmaps);
        this.kubernetesConfigmaps
                = kubernetesConfigmaps instanceof KubernetesConfigmapsVaultConfigurationProperties p ? p : null;
    }

    @Override
    public IBMSecretsManagerVaultConfiguration getIBMSecretsManagerVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return ibmSecretsManager != null ? ibmSecretsManager : super.getIBMSecretsManagerVaultConfiguration();
    }

    @Override
    public void setIBMSecretsManagerVaultConfiguration(IBMSecretsManagerVaultConfiguration ibmSecretsManager) {
        super.setIBMSecretsManagerVaultConfiguration(ibmSecretsManager);
        this.ibmSecretsManager = ibmSecretsManager instanceof IBMSecretsManagerVaultConfigurationProperties p ? p : null;
    }

    @Override
    public SpringCloudConfigConfiguration getSpringCloudConfigConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return springConfig != null ? springConfig : super.getSpringCloudConfigConfiguration();
    }

    @Override
    public void setSpringCloudConfigConfiguration(SpringCloudConfigConfiguration springConfig) {
        super.setSpringCloudConfigConfiguration(springConfig);
        this.springConfig = springConfig instanceof SpringCloudConfigConfigurationProperties p ? p : null;
    }

    @Override
    public CyberArkVaultConfiguration getCyberArkVaultConfiguration() {
        // the configuration from the fluent builder, or else what has been set
        return cyberark != null ? cyberark : super.getCyberArkVaultConfiguration();
    }

    @Override
    public void setCyberArkVaultConfiguration(CyberArkVaultConfiguration cyberark) {
        super.setCyberArkVaultConfiguration(cyberark);
        this.cyberark = cyberark instanceof CyberArkVaultConfigurationProperties p ? p : null;
    }

    // fluent builders
    // --------------------------------------------------------------

    @Override
    public AwsVaultConfigurationProperties aws() {
        if (aws == null) {
            aws = new AwsVaultConfigurationProperties(parent);
        }
        return aws;
    }

    @Override
    public GcpVaultConfigurationProperties gcp() {
        if (gcp == null) {
            gcp = new GcpVaultConfigurationProperties(parent);
        }
        return gcp;
    }

    @Override
    public AzureVaultConfigurationProperties azure() {
        if (azure == null) {
            azure = new AzureVaultConfigurationProperties(parent);
        }
        return azure;
    }

    @Override
    public HashicorpVaultConfigurationProperties hashicorp() {
        if (hashicorp == null) {
            hashicorp = new HashicorpVaultConfigurationProperties(parent);
        }
        return hashicorp;
    }

    @Override
    public KubernetesVaultConfigurationProperties kubernetes() {
        if (kubernetes == null) {
            kubernetes = new KubernetesVaultConfigurationProperties(parent);
        }
        return kubernetes;
    }

    @Override
    public KubernetesConfigmapsVaultConfigurationProperties kubernetesConfigmaps() {
        if (kubernetesConfigmaps == null) {
            kubernetesConfigmaps = new KubernetesConfigmapsVaultConfigurationProperties(parent);
        }
        return kubernetesConfigmaps;
    }

    @Override
    public IBMSecretsManagerVaultConfigurationProperties ibmSecretsManager() {
        if (ibmSecretsManager == null) {
            ibmSecretsManager = new IBMSecretsManagerVaultConfigurationProperties(parent);
        }
        return ibmSecretsManager;
    }

    @Override
    public SpringCloudConfigConfigurationProperties springConfig() {
        if (springConfig == null) {
            springConfig = new SpringCloudConfigConfigurationProperties(parent);
        }
        return springConfig;
    }

    @Override
    public CyberArkVaultConfigurationProperties cyberark() {
        if (cyberark == null) {
            cyberark = new CyberArkVaultConfigurationProperties(parent);
        }
        return cyberark;
    }
}
