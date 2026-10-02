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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import org.apache.camel.Exchange;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods for Kubernetes resources.
 */
public final class KubernetesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesHelper.class);

    private KubernetesHelper() {
    }

    public static KubernetesClient getKubernetesClient(KubernetesConfiguration configuration) {
        if (configuration.getKubernetesClient() != null) {
            return configuration.getKubernetesClient();
        }
        String master = configuration.getMasterUrl();
        if (master == null || "local".equals(master) || "client".equals(master)) {
            LOG.info("Creating default local Kubernetes client without applying configuration");
            return new KubernetesClientBuilder().build();
        } else {
            return createKubernetesClient(configuration);
        }
    }

    private static KubernetesClient createKubernetesClient(KubernetesConfiguration configuration) {
        LOG.debug("Create Kubernetes client with the following Configuration: {}", configuration);

        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(configuration.getMasterUrl());
        if (ObjectHelper.isNotEmpty(configuration.getUsername()) && ObjectHelper.isNotEmpty(configuration.getPassword())
                && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        }

        ObjectHelper.ifNotEmpty(configuration.getOauthToken(), builder::withOauthToken);
        ObjectHelper.ifNotEmpty(configuration.getCaCertData(), builder::withCaCertData);
        ObjectHelper.ifNotEmpty(configuration.getCaCertFile(), builder::withCaCertFile);
        ObjectHelper.ifNotEmpty(configuration.getClientCertData(), builder::withClientCertData);
        ObjectHelper.ifNotEmpty(configuration.getClientCertFile(), builder::withClientCertFile);
        ObjectHelper.ifNotEmpty(configuration.getApiVersion(), builder::withApiVersion);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyAlgo(), builder::withClientKeyAlgo);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyData(), builder::withClientKeyData);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyFile(), builder::withClientKeyFile);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyPassphrase(), builder::withClientKeyPassphrase);
        ObjectHelper.ifNotEmpty(configuration.getTrustCerts(), builder::withTrustCerts);
        ObjectHelper.ifNotEmpty(configuration.getConnectionTimeout(), builder::withConnectionTimeout);
        ObjectHelper.ifNotEmpty(configuration.getNamespace(), builder::withNamespace);

        Config conf = builder.build();
        return new KubernetesClientBuilder().withConfig(conf).build();
    }

    public static void close(Runnable runnable, Supplier<Watch> watchGetter) {
        if (runnable != null) {
            final Watch watch = watchGetter.get();
            if (watch != null) {
                watch.close();
            }
        }
    }

    /**
     * Watches again when the Kubernetes client closed the watch of a consumer with an error. The client reconnects a
     * watch by itself after transient errors, and only closes it with an exception when it gives up: when the API
     * server answers 410 Gone because the resource version of the watch is too old (which happens to long-running
     * watches), or when the reconnect limit is reached. The consumer would then not receive any event anymore.
     *
     * @param consumer the consumer of the watch
     * @param executor the executor of the consumer
     * @param task     the task that creates the watch of the consumer
     */
    public static void watchAgain(ServiceSupport consumer, ExecutorService executor, Runnable task) {
        if (consumer.isRunAllowed() && executor != null && !executor.isShutdown()) {
            LOG.info("Watching again for {} after its watch was closed", consumer);
            try {
                executor.submit(task);
            } catch (RejectedExecutionException e) {
                LOG.debug("Cannot watch again for {} as it is stopping", consumer, e);
            }
        }
    }

    public static String extractOperation(AbstractKubernetesEndpoint endpoint, Exchange exchange) {
        String operation;

        if (ObjectHelper.isEmpty(endpoint.getKubernetesConfiguration().getOperation())) {
            operation = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = endpoint.getKubernetesConfiguration().getOperation();
        }

        if (ObjectHelper.isEmpty(operation)) {
            throw new IllegalArgumentException("The kubernetes producer for this component requires a operation to proceed");
        }

        return operation;
    }

    public static void prepareOutboundMessage(Exchange exchange, Object body) {
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), true);
        exchange.getMessage().setBody(body);
    }

}
