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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.component.kubernetes.consumer.common.ServiceEvent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesServicesConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesServicesConsumer.class);

    private final Processor processor;
    private ExecutorService executor;

    public KubernetesServicesConsumer(KubernetesEndpoint endpoint, Processor processor) {
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

        executor.submit(new ServicesConsumerTask());       

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.debug("Stopping Kubernetes Services Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }
    
    class ServicesConsumerTask implements Runnable {
        
        @Override
        public void run() {
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getOauthToken())) {
                if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespace())) {
                    getEndpoint().getKubernetesClient().services()
                            .inNamespace(getEndpoint().getKubernetesConfiguration().getNamespace())
                            .watch(new Watcher<Service>() {

                                @Override
                                public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                                        Service resource) {
                                    ServiceEvent se = new ServiceEvent(action, resource);
                                    Exchange exchange = getEndpoint().createExchange();
                                    exchange.getIn().setBody(se.getService());
                                    exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, se.getAction());
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
                } else {
                    getEndpoint().getKubernetesClient().services().watch(new Watcher<Service>() {

                        @Override
                        public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Service resource) {
                            ServiceEvent se = new ServiceEvent(action, resource);
                            Exchange exchange = getEndpoint().createExchange();
                            exchange.getIn().setBody(se.getService());
                            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, se.getAction());
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
    }

}
