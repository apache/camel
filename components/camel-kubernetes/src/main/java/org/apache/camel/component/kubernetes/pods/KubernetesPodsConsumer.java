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
package org.apache.camel.component.kubernetes.pods;

import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.consumer.common.PodEvent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesPodsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPodsConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private PodsConsumerTask podsWatcher;

    public KubernetesPodsConsumer(AbstractKubernetesEndpoint endpoint, Processor processor) {
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
        
        podsWatcher = new PodsConsumerTask();
        executor.submit(podsWatcher);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping Kubernetes Pods Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                if (podsWatcher != null) {
                    podsWatcher.getWatch().close();
                }
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                if (podsWatcher != null) {
                    podsWatcher.getWatch().close();
                }
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class PodsConsumerTask implements Runnable {

        private Watch watch;
        
        @Override
        public void run() {
            MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> w = getEndpoint().getKubernetesClient().pods();
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespace())) {
                w.inNamespace(getEndpoint().getKubernetesConfiguration().getNamespace());
            }
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelKey()) 
                && ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelValue())) {
                w.withLabel(getEndpoint().getKubernetesConfiguration().getLabelKey(), getEndpoint().getKubernetesConfiguration().getLabelValue());
            }
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getResourceName())) {
                w.withName(getEndpoint().getKubernetesConfiguration().getResourceName());
            }
            watch = w.watch(new Watcher<Pod>() {

                @Override
                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                    Pod resource) {
                    PodEvent pe = new PodEvent(action, resource);
                    Exchange exchange = getEndpoint().createExchange();
                    exchange.getIn().setBody(pe.getPod());
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, pe.getAction());
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TIMESTAMP, System.currentTimeMillis());
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
        }

        public Watch getWatch() {
            return watch;
        }

        public void setWatch(Watch watch) {
            this.watch = watch;
        } 
    }
}
