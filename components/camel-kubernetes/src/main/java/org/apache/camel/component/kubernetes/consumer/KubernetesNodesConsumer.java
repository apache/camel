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
package org.apache.camel.component.kubernetes.consumer;

import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.component.kubernetes.consumer.common.NodeEvent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesNodesConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNodesConsumer.class);

    private final Processor processor;
    private ExecutorService executor;

    public KubernetesNodesConsumer(KubernetesEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public KubernetesEndpoint getEndpoint() {
        return (KubernetesEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = getEndpoint().createExecutor();

        executor.submit(new NodesConsumerTask());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping Kubernetes Nodes Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class NodesConsumerTask implements Runnable {
        
        @Override
        public void run() {
            ClientNonNamespaceOperation<Node, NodeList, DoneableNode, ClientResource<Node, DoneableNode>> w = getEndpoint().getKubernetesClient().nodes();
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelKey()) 
                && ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getLabelValue())) {
                w.withLabel(getEndpoint().getKubernetesConfiguration().getLabelKey(), getEndpoint().getKubernetesConfiguration().getLabelValue());
            }
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getResourceName())) {
                w.withName(getEndpoint().getKubernetesConfiguration().getResourceName());
            }
            w.watch(new Watcher<Node>() {

                @Override
                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                    Node resource) {
                    NodeEvent ne = new NodeEvent(action, resource);
                    Exchange exchange = getEndpoint().createExchange();
                    exchange.getIn().setBody(ne.getNode());
                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, ne.getAction());
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
    }
}
