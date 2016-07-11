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

import io.fabric8.kubernetes.api.model.DoneableNamespace;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.component.kubernetes.consumer.common.NamespaceEvent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesNamespacesConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNamespacesConsumer.class);
    
    private final Processor processor;
    private ExecutorService executor;

    public KubernetesNamespacesConsumer(KubernetesEndpoint endpoint, Processor processor) {
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
        
        executor.submit(new NamespacesConsumerTask());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        
        LOG.debug("Stopping Kubernetes Namespace Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }
    
    class NamespacesConsumerTask implements Runnable {
        
        @Override
        public void run() {
            ClientNonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, ClientResource<Namespace, DoneableNamespace>> w = getEndpoint().getKubernetesClient().namespaces();
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespace())) {
                w.withName(getEndpoint().getKubernetesConfiguration().getNamespace());
            }
            w.watch(new Watcher<Namespace>() {

                @Override
                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                    Namespace resource) {
                    NamespaceEvent ne = new NamespaceEvent(action, resource);
                    Exchange exchange = getEndpoint().createExchange();
                    exchange.getIn().setBody(ne.getNamespace());
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

