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
package org.apache.camel.component.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kubernetes.consumer.KubernetesPodsConsumer;
import org.apache.camel.component.kubernetes.consumer.KubernetesReplicationControllersConsumer;
import org.apache.camel.component.kubernetes.consumer.KubernetesSecretsConsumer;
import org.apache.camel.component.kubernetes.consumer.KubernetesServicesConsumer;
import org.apache.camel.component.kubernetes.producer.KubernetesBuildConfigsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesBuildsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesNamespacesProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesNodesProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesPersistentVolumesClaimsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesPersistentVolumesProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesPodsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesReplicationControllersProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesResourcesQuotaProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesSecretsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesServiceAccountsProducer;
import org.apache.camel.component.kubernetes.producer.KubernetesServicesProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "kubernetes", title = "Kubernetes", syntax = "kubernetes:master", label = "cloud,paas")
public class KubernetesEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesEndpoint.class);

    @UriParam
    private KubernetesConfiguration configuration;

    private DefaultKubernetesClient client;

    public KubernetesEndpoint(String uri, KubernetesComponent component, KubernetesConfiguration config) {
        super(uri, component);
        this.configuration = config;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getCategory())) {
            throw new IllegalArgumentException("A producer category must be specified");
        } else {
            String category = configuration.getCategory();

            switch (category) {

            case KubernetesCategory.NAMESPACES:
                return new KubernetesNamespacesProducer(this);

            case KubernetesCategory.SERVICES:
                return new KubernetesServicesProducer(this);

            case KubernetesCategory.REPLICATION_CONTROLLERS:
                return new KubernetesReplicationControllersProducer(this);

            case KubernetesCategory.PODS:
                return new KubernetesPodsProducer(this);

            case KubernetesCategory.PERSISTENT_VOLUMES:
                return new KubernetesPersistentVolumesProducer(this);

            case KubernetesCategory.PERSISTENT_VOLUMES_CLAIMS:
                return new KubernetesPersistentVolumesClaimsProducer(this);

            case KubernetesCategory.SECRETS:
                return new KubernetesSecretsProducer(this);

            case KubernetesCategory.RESOURCES_QUOTA:
                return new KubernetesResourcesQuotaProducer(this);

            case KubernetesCategory.SERVICE_ACCOUNTS:
                return new KubernetesServiceAccountsProducer(this);

            case KubernetesCategory.NODES:
                return new KubernetesNodesProducer(this);

            case KubernetesCategory.BUILDS:
                return new KubernetesBuildsProducer(this);

            case KubernetesCategory.BUILD_CONFIGS:
                return new KubernetesBuildConfigsProducer(this);

            default:
                throw new IllegalArgumentException("The " + category + " producer category doesn't exist");
            }
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getCategory())) {
            throw new IllegalArgumentException("A consumer category must be specified");
        } else {
            String category = configuration.getCategory();

            switch (category) {

            case KubernetesCategory.PODS:
                return new KubernetesPodsConsumer(this, processor);

            case KubernetesCategory.SERVICES:
                return new KubernetesServicesConsumer(this, processor);

            case KubernetesCategory.REPLICATION_CONTROLLERS:
                return new KubernetesReplicationControllersConsumer(this, processor);

            case KubernetesCategory.SECRETS:
                return new KubernetesSecretsConsumer(this, processor);

            default:
                throw new IllegalArgumentException("The " + category + " consumer category doesn't exist");
            }
        }
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        client = configuration.getKubernetesClient() != null ? configuration.getKubernetesClient()
                : createKubernetesClient();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        client.close();
    }

    public DefaultKubernetesClient getKubernetesClient() {
        return client;
    }

    /**
     * The kubernetes Configuration
     */
    public KubernetesConfiguration getKubernetesConfiguration() {
        return configuration;
    }

    private DefaultKubernetesClient createKubernetesClient() {
        LOG.debug("Create Kubernetes client with the following Configuration: " + configuration.toString());

        DefaultKubernetesClient kubeClient = new DefaultKubernetesClient();
        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(configuration.getMasterUrl());
        if ((ObjectHelper.isNotEmpty(configuration.getUsername())
                && ObjectHelper.isNotEmpty(configuration.getPassword()))
                && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        } else {
            builder.withOauthToken(configuration.getOauthToken());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertData())) {
            builder.withCaCertData(configuration.getCaCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertFile())) {
            builder.withCaCertFile(configuration.getCaCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertData())) {
            builder.withClientCertData(configuration.getClientCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertFile())) {
            builder.withClientCertFile(configuration.getClientCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getApiVersion())) {
            builder.withApiVersion(configuration.getApiVersion());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyAlgo())) {
            builder.withClientKeyAlgo(configuration.getClientKeyAlgo());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyData())) {
            builder.withClientKeyData(configuration.getClientKeyData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyFile())) {
            builder.withClientKeyFile(configuration.getClientKeyFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyPassphrase())) {
            builder.withClientKeyPassphrase(configuration.getClientKeyPassphrase());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTrustCerts())) {
            builder.withTrustCerts(configuration.getTrustCerts());
        }

        Config conf = builder.build();

        kubeClient = new DefaultKubernetesClient(conf);
        return kubeClient;
    }
}
