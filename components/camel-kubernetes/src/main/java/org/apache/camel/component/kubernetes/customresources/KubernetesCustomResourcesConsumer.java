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
package org.apache.camel.component.kubernetes.customresources;

import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesCustomResourcesConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesCustomResourcesConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private CustomResourcesConsumerTask customResourcesWatcher;

    public KubernetesCustomResourcesConsumer(AbstractKubernetesEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = getEndpoint().createExecutor();

        customResourcesWatcher = new CustomResourcesConsumerTask();
        executor.submit(customResourcesWatcher);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping Kubernetes Custom Resources Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                if (customResourcesWatcher != null) {
                    customResourcesWatcher.getWatch().close();
                }
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                if (customResourcesWatcher != null) {
                    customResourcesWatcher.getWatch().close();
                }
                executor.shutdownNow();
            }
        }
        executor = null;
        super.doStop();
    }

    class CustomResourcesConsumerTask implements Runnable {

        private Watch watch;

        @Override
        public void run() {
            RawCustomResourceOperationsImpl operations = getEndpoint().getKubernetesClient()
                    .customResource(getCRDContext(getEndpoint().getKubernetesConfiguration()));
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespace())) {
                LOG.error("namespace is not specified.");
            }
            String namespace = getEndpoint().getKubernetesConfiguration().getNamespace();
            try {
                operations.watch(namespace, new Watcher<String>() {

                    @Override
                    public void eventReceived(Action action, String resource) {
                        Exchange exchange = getEndpoint().createExchange();
                        exchange.getIn().setBody(resource);
                        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_EVENT_ACTION, action);
                        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_EVENT_TIMESTAMP,
                                System.currentTimeMillis());
                        try {
                            processor.process(exchange);
                        } catch (Exception e) {
                            getExceptionHandler().handleException("Error during processing", exchange, e);
                        }
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        if (cause != null) {
                            LOG.error(cause.getMessage(), cause);
                        }

                    }
                });
            } catch (Exception e) {
                LOG.error("Exception in handling githubsource instance change", e);
            }
        }

        public Watch getWatch() {
            return watch;
        }

        public void setWatch(Watch watch) {
            this.watch = watch;
        }
    }

    private CustomResourceDefinitionContext getCRDContext(KubernetesConfiguration config) {
        if (ObjectHelper.isEmpty(config.getCrdName()) || ObjectHelper.isEmpty(config.getCrdGroup())
                || ObjectHelper.isEmpty(config.getCrdScope())
                || ObjectHelper.isEmpty(config.getCrdVersion()) || ObjectHelper.isEmpty(config.getCrdPlural())) {
            LOG.error("one of more of the custom resource definition argument(s) are missing.");
            throw new IllegalArgumentException("one of more of the custom resource definition argument(s) are missing.");
        }

        CustomResourceDefinitionContext cRDContext = new CustomResourceDefinitionContext.Builder()
                .withName(config.getCrdName())       // example: "githubsources.sources.knative.dev"
                .withGroup(config.getCrdGroup())     // example: "sources.knative.dev"
                .withScope(config.getCrdScope())     // example: "Namespaced"
                .withVersion(config.getCrdVersion()) // example: "v1alpha1"
                .withPlural(config.getCrdPlural())   // example: "githubsources"
                .build();
        return cRDContext;
    }
}
