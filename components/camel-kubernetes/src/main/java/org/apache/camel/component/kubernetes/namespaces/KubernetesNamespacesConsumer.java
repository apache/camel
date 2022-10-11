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
package org.apache.camel.component.kubernetes.namespaces;

import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesNamespacesConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNamespacesConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private NamespacesConsumerTask nsWatcher;

    public KubernetesNamespacesConsumer(AbstractKubernetesEndpoint endpoint, Processor processor) {
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

        nsWatcher = new NamespacesConsumerTask();
        executor.submit(nsWatcher);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping Kubernetes Namespace Consumer");
        if (executor != null) {
            KubernetesHelper.close(nsWatcher, nsWatcher::getWatch);

            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class NamespacesConsumerTask implements Runnable {

        private Watch watch;

        @Override
        public void run() {
            NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> w
                    = getEndpoint().getKubernetesClient().namespaces();

            ObjectHelper.ifNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespace(), w::withName);

            watch = w.watch(new Watcher<Namespace>() {

                @Override
                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Namespace resource) {
                    Exchange exchange = createExchange(false);
                    exchange.getIn().setBody(resource);
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, action);
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TIMESTAMP, System.currentTimeMillis());
                    try {
                        processor.process(exchange);
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error during processing", exchange, e);
                    } finally {
                        releaseExchange(exchange, false);
                    }
                }

                @Override
                public void onClose(WatcherException cause) {
                    if (cause != null) {
                        LOG.error(cause.getMessage(), cause);
                    }
                }
            });
        }

        public Watch getWatch() {
            return watch;
        }

        public void setWatch(Watch watch) {
            this.watch = watch;
        }
    }
}
