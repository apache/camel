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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class RouteboxConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(RouteboxConfiguration.class);

    private URI uri;
    private String authority;

    @UriPath @Metadata(required = "true")
    private String routeboxName;
    @UriParam
    private RouteboxDispatchStrategy dispatchStrategy;
    @UriParam
    private Map<String, String> dispatchMap;
    @UriParam(defaultValue = "true")
    private boolean forkContext = true;
    @UriParam(label = "producer", defaultValue = "20000")
    private long connectionTimeout = 20000;
    @UriParam(label = "consumer", defaultValue = "1000")
    private long pollInterval = 1000;
    @UriParam(defaultValue = "direct", enums = "direct,seda")
    private String innerProtocol = "direct";
    @UriParam(label = "consumer", defaultValue = "20")
    private int threads = 20;
    @UriParam
    private int queueSize;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean sendToConsumer = true;
    @UriParam(label = "advanced")
    private CamelContext innerContext;
    @UriParam(label = "advanced")
    private Registry innerRegistry;
    @UriParam(label = "advanced")
    private ProducerTemplate innerProducerTemplate;
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>();

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
        
        setRouteboxName(getAuthority());
        
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
            routeBuilders = component.resolveAndRemoveReferenceParameter(parameters, "routeBuilders", List.class);
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

        dispatchStrategy = component.resolveAndRemoveReferenceParameter(parameters, "dispatchStrategy", RouteboxDispatchStrategy.class, null);
        dispatchMap = component.resolveAndRemoveReferenceParameter(parameters, "dispatchMap", HashMap.class, new HashMap<String, String>());
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

    /**
     * A string representing a key in the Camel Registry matching an object value of the type org.apache.camel.CamelContext.
     * If a CamelContext is not provided by the user a CamelContext is automatically created for deployment of inner routes.
     */
    public void setInnerContext(CamelContext innerContext) {
        this.innerContext = innerContext;
    }

    /**
     * A string representing a key in the Camel Registry matching an object value of the type List<org.apache.camel.builder.RouteBuilder>.
     * If the user does not supply an innerContext pre-primed with inner routes, the routeBuilders option must be provided as a non-empty
     * list of RouteBuilders containing inner routes
     */
    public void setRouteBuilders(List<RouteBuilder> routeBuilders) {
        this.routeBuilders = routeBuilders;
    }

    public List<RouteBuilder> getRouteBuilders() {
        return routeBuilders;
    }

    /**
     * Whether to fork and create a new inner CamelContext instead of reusing the same CamelContext.
     */
    public void setForkContext(boolean forkContext) {
        this.forkContext = forkContext;
    }
    
    public boolean isForkContext() {
        return forkContext;
    }

    /**
     * Number of threads to be used by the routebox to receive requests.
     */
    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getThreads() {
        return threads;
    }

    /**
     * Logical name for the routebox (eg like a queue name)
     */
    public void setRouteboxName(String routeboxName) {
        this.routeboxName = routeboxName;
    }

    public String getRouteboxName() {
        return routeboxName;
    }

    /**
     * To use a custom RouteboxDispatchStrategy which allows to use custom dispatching instead of the default.
     */
    public void setDispatchStrategy(RouteboxDispatchStrategy dispatchStrategy) {
        this.dispatchStrategy = dispatchStrategy;
    }

    public RouteboxDispatchStrategy getDispatchStrategy() {
        return dispatchStrategy;
    }

    /**
     * Timeout in millis used by the producer when sending a message.
     */
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    /**
     * The timeout used when polling from seda.
     * When a timeout occurs, the consumer can check whether it is allowed to continue running.
     * Setting a lower value allows the consumer to react more quickly upon shutdown.
     */
    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    /**
     * Create a fixed size queue to receive requests.
     */
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setInnerProducerTemplate(ProducerTemplate innerProducerTemplate) {
        this.innerProducerTemplate = innerProducerTemplate;
    }

    /**
     * The ProducerTemplate to use by the internal embeded CamelContext
     */
    public ProducerTemplate getInnerProducerTemplate() {
        return innerProducerTemplate;
    }

    /**
     * The Protocol used internally by the Routebox component. Can be Direct or SEDA. The Routebox component currently offers protocols that are JVM bound.
     */
    public void setInnerProtocol(String innerProtocol) {
        this.innerProtocol = innerProtocol;
    }

    public String getInnerProtocol() {
        return innerProtocol;
    }

    /**
     * To use a custom registry for the internal embedded CamelContext.
     */
    public void setInnerRegistry(Registry innerRegistry) {
        this.innerRegistry = innerRegistry;
    }

    public Registry getInnerRegistry() {
        return innerRegistry;
    }

    /**
     * Dictates whether a Producer endpoint sends a request to an external routebox consumer.
     * If the setting is false, the Producer creates an embedded inner context and processes requests internally.
     */
    public void setSendToConsumer(boolean sendToConsumer) {
        this.sendToConsumer = sendToConsumer;
    }

    public boolean isSendToConsumer() {
        return sendToConsumer;
    }

    /**
     * A string representing a key in the Camel Registry matching an object value of the type HashMap<String, String>.
     * The HashMap key should contain strings that can be matched against the value set for the exchange header ROUTE_DISPATCH_KEY.
     * The HashMap value should contain inner route consumer URI's to which requests should be directed.
     */
    public void setDispatchMap(Map<String, String> dispatchMap) {
        this.dispatchMap = dispatchMap;
    }

    public Map<String, String> getDispatchMap() {
        return dispatchMap;
    }

}
