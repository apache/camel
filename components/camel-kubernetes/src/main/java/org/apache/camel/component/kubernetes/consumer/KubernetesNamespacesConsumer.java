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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.component.kubernetes.consumer.common.NamespaceEvent;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesNamespacesConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNamespacesConsumer.class);

    private ConcurrentMap<Long, NamespaceEvent> map;

    public KubernetesNamespacesConsumer(KubernetesEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public KubernetesEndpoint getEndpoint() {
        return (KubernetesEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        map = new ConcurrentHashMap<Long, NamespaceEvent>();

        if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getOauthToken())) {
            if (ObjectHelper.isNotEmpty(getEndpoint().getKubernetesConfiguration().getNamespaceName())) {
                getEndpoint().getKubernetesClient().namespaces()
                        .withName(getEndpoint().getKubernetesConfiguration().getNamespaceName())
                        .watch(new Watcher<Namespace>() {

                            @Override
                            public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                                    Namespace resource) {
                                NamespaceEvent ne = new NamespaceEvent(action, resource);
                                map.put(System.currentTimeMillis(), ne);
                                
                            }

                            @Override
                            public void onClose(KubernetesClientException cause) {
                                if (cause != null) {
                                    LOG.error(cause.getMessage(), cause);
                                }                            
                            }
                        });
            } else {
                getEndpoint().getKubernetesClient().namespaces().watch(new Watcher<Namespace>() {

                    @Override
                    public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action,
                            Namespace resource) {
                        NamespaceEvent ne = new NamespaceEvent(action, resource);
                        map.put(System.currentTimeMillis(), ne);
                        
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

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        map.clear();
    }

    @Override
    protected int poll() throws Exception {
        int mapSize = map.size();
        for (ConcurrentMap.Entry<Long, NamespaceEvent> entry : map.entrySet()) {
            NamespaceEvent namespaceEvent = (NamespaceEvent) entry.getValue();
            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(namespaceEvent.getNamespace());
            e.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION, namespaceEvent.getAction());
            e.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TIMESTAMP, entry.getKey());
            getProcessor().process(e);
            map.remove(entry.getKey());
        }
        return mapSize;
    }

}
