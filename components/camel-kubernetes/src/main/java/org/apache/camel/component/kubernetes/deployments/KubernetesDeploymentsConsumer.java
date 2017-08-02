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
package org.apache.camel.component.kubernetes.deployments;

import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.DoneableDeployment;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.component.kubernetes.consumer.common.DeploymentEvent;
import org.apache.camel.component.kubernetes.consumer.common.NodeEvent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDeploymentsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDeploymentsConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private DeploymentsConsumerTask deploymentsWatcher;

    public KubernetesDeploymentsConsumer(AbstractKubernetesEndpoint endpoint, Processor processor) {
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

        deploymentsWatcher = new DeploymentsConsumerTask();
        executor.submit(deploymentsWatcher);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping Kubernetes Deployments Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                if (deploymentsWatcher != null) {
                    deploymentsWatcher.getWatch().close();
                }
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                if (deploymentsWatcher != null) {
                    deploymentsWatcher.getWatch().close();
                }
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class DeploymentsConsumerTask implements Runnable {
        
        private Watch watch;
        
        @Override
        public void run() {
            NonNamespaceOperation<Deployment, DeploymentList, DoneableDeployment, ScalableResource<Deployment, DoneableDeployment>> w = getEndpoint().getKubernetesClient().extensions().deployments();
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelKey()) 
                && ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelValue())) {
                w.withLabel(getEndpoint().getKubernetesConfiguration().getLabelKey(), getEndpoint().getKubernetesConfiguration().getLabelValue());
            }
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getResourceName())) {
                w.withName(getEndpoint().getKubernetesConfiguration().getResourceName());
            }
            watch = w.watch(new Watcher<Deployment>() {

                @Override
                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                    Deployment resource) {
                    DeploymentEvent de = new DeploymentEvent(action, resource);
                    Exchange exchange = getEndpoint().createExchange();
                    exchange.getIn().setBody(de.getDeployment());
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, de.getAction());
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
