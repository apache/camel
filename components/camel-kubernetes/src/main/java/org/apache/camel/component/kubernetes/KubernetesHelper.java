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
package org.apache.camel.component.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper moethods for Kubernetes resources.
 */
public final class KubernetesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesHelper.class);

    private KubernetesHelper() {
    }

    public static KubernetesClient getKubernetesClient(KubernetesConfiguration configuration) {
        if (configuration.getKubernetesClient() != null) {
            return configuration.getKubernetesClient();
        } else if (configuration.getMasterUrl() != null) {
            return createKubernetesClient(configuration);
        } else {
            LOG.info("Creating default kubernetes client without applying configuration");
            return new DefaultKubernetesClient();
        }
    }

    private static KubernetesClient createKubernetesClient(KubernetesConfiguration configuration) {
        LOG.debug("Create Kubernetes client with the following Configuration: {}", configuration);

        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(configuration.getMasterUrl());
        if ((ObjectHelper.isNotEmpty(configuration.getUsername()) && ObjectHelper.isNotEmpty(configuration.getPassword())) && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        }
        if (ObjectHelper.isNotEmpty(configuration.getOauthToken())) {
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
        if (ObjectHelper.isNotEmpty(configuration.getConnectionTimeout())) {
            builder.withConnectionTimeout(configuration.getConnectionTimeout());
        }
        if (ObjectHelper.isNotEmpty(configuration.getNamespace())) {
            builder.withNamespace(configuration.getNamespace());
        }
        Config conf = builder.build();
        return new DefaultKubernetesClient(conf);
    }

}
