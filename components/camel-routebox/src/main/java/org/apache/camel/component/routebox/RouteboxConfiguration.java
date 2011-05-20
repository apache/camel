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
package org.apache.camel.component.routebox;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.routebox.strategy.RouteboxDispatchStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteboxConfiguration {
    private static final transient Logger LOG = LoggerFactory.getLogger(RouteboxConfiguration.class);
    private URI uri;
    private String authority;
    private String endpointName;
    private URI consumerUri;
    private URI producerUri;
    private RouteboxDispatchStrategy dispatchStrategy;
    private Map<String, String> dispatchMap;
    private CamelContext innerContext;
    private List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>();
    private Registry innerRegistry;
    private boolean forkContext = true;
    private boolean local = true;
    private long connectionTimeout = 20000;
    private long pollInterval = 1000;
    private String innerProtocol;
    private int threads = 20;
    private int queueSize;
    private ProducerTemplate innerProducerTemplate;
    private boolean sendToConsumer = true;

    public RouteboxConfiguration() {
    }

    public RouteboxConfiguration(URI uri) {
        this();
        this.uri = uri;
    }

    @SuppressWarnings("unchecked")
    public void parseURI(URI uri, Map<String, Object> parameters, RouteboxComponent component) throws Exception {
        String protocol = uri.getScheme();
        
        if (!protocol.equalsIgnoreCase("routebox")) {
            throw new IllegalArgumentException("Unrecognized protocol: " + protocol + " for uri: " + uri);
        }
        
        setUri(uri);
        setAuthority(uri.getAuthority());
        if (LOG.isTraceEnabled()) {
            LOG.trace("Authority: {}", uri.getAuthority());
        }
        
        setEndpointName(getAuthority());
        
        if (parameters.containsKey("threads")) {
            setThreads(Integer.valueOf((String) parameters.get("threads")));
        }
        
        if (parameters.containsKey("forkContext")) {
            if (!(Boolean.valueOf((String) parameters.get("forkContext")))) {
                setForkContext(false);
            }
        }
        
        if (parameters.containsKey("innerProtocol")) {
            setInnerProtocol((String) parameters.get("innerProtocol"));
            if ((!innerProtocol.equalsIgnoreCase("seda")) && (!innerProtocol.equalsIgnoreCase("direct"))) {
                throw new IllegalArgumentException("Unrecognized inner protocol: " + innerProtocol + " for uri: " + uri);
            }
        } else {
            setInnerProtocol("direct");
        }
        
        if (parameters.containsKey("sendToConsumer")) {
            if (!Boolean.valueOf((String) parameters.get("sendToConsumer"))) {
                setSendToConsumer(false);
            }
        }
        
        if (parameters.containsKey("connectionTimeout")) {
            setConnectionTimeout(Long.parseLong((String) parameters.get("connectionTimeout")));
        }
        
        if (parameters.containsKey("pollInterval")) {
            setConnectionTimeout(Long.parseLong((String) parameters.get("pollInterval")));
        }
        
        if (parameters.containsKey("routeBuilders")) {
            routeBuilders = (List<RouteBuilder>) component.resolveAndRemoveReferenceParameter(parameters, "routeBuilders", List.class);
        }
        
        if (parameters.containsKey("innerRegistry")) {
            innerRegistry = component.resolveAndRemoveReferenceParameter(parameters, "innerRegistry", Registry.class);
        }
        
        if (isForkContext()) {
            if (innerRegistry != null) {
                innerContext = component.resolveAndRemoveReferenceParameter(parameters, "innerContext", CamelContext.class, new DefaultCamelContext(innerRegistry));
            } else {
                innerContext = component.resolveAndRemoveReferenceParameter(parameters, "innerContext", CamelContext.class, new DefaultCamelContext());
            }
        } else {
            innerContext = component.getCamelContext();
        }
        
        innerProducerTemplate = innerContext.createProducerTemplate();
        setQueueSize(component.getAndRemoveParameter(parameters, "size", Integer.class, 0));
        consumerUri = component.resolveAndRemoveReferenceParameter(parameters, "consumerUri", URI.class, new URI("routebox:" + getEndpointName()));
        producerUri = component.resolveAndRemoveReferenceParameter(parameters, "producerUri", URI.class, new URI("routebox:" + getEndpointName()));        
        
        dispatchStrategy = component.resolveAndRemoveReferenceParameter(parameters, "dispatchStrategy", RouteboxDispatchStrategy.class, null);
        dispatchMap = (HashMap<String, String>) component.resolveAndRemoveReferenceParameter(parameters, "dispatchMap", HashMap.class, new HashMap<String, String>());
        if (dispatchStrategy == null && dispatchMap == null) {
            LOG.warn("No Routebox Dispatch Map or Strategy has been set. Routebox may not have more than one inner route.");
        }        
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public CamelContext getInnerContext() {
        return innerContext;
    }

    public void setInnerContext(CamelContext innerContext) {
        this.innerContext = innerContext;
    }

    public void setRouteBuilders(List<RouteBuilder> routeBuilders) {
        this.routeBuilders = routeBuilders;
    }

    public List<RouteBuilder> getRouteBuilders() {
        return routeBuilders;
    }

    public void setForkContext(boolean forkContext) {
        this.forkContext = forkContext;
    }
    
    public boolean isForkContext() {
        return forkContext;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getThreads() {
        return threads;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isLocal() {
        return local;
    }

    public void setProducerUri(URI producerUri) {
        this.producerUri = producerUri;
    }

    public URI getProducerUri() {
        return producerUri;
    }

    public void setConsumerUri(URI consumerUri) {
        this.consumerUri = consumerUri;
    }

    public URI getConsumerUri() {
        return consumerUri;
    }

    public void setDispatchStrategy(RouteboxDispatchStrategy dispatchStrategy) {
        this.dispatchStrategy = dispatchStrategy;
    }

    public RouteboxDispatchStrategy getDispatchStrategy() {
        return dispatchStrategy;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setInnerProducerTemplate(ProducerTemplate innerProducerTemplate) {
        this.innerProducerTemplate = innerProducerTemplate;
    }

    public ProducerTemplate getInnerProducerTemplate() {
        return innerProducerTemplate;
    }

    public void setInnerProtocol(String innerProtocol) {
        this.innerProtocol = innerProtocol;
    }

    public String getInnerProtocol() {
        return innerProtocol;
    }

    public void setInnerRegistry(Registry innerRegistry) {
        this.innerRegistry = innerRegistry;
    }

    public Registry getInnerRegistry() {
        return innerRegistry;
    }

    public void setSendToConsumer(boolean sendToConsumer) {
        this.sendToConsumer = sendToConsumer;
    }

    public boolean isSendToConsumer() {
        return sendToConsumer;
    }

    public void setDispatchMap(Map<String, String> dispatchMap) {
        this.dispatchMap = dispatchMap;
    }

    public Map<String, String> getDispatchMap() {
        return dispatchMap;
    }

}
