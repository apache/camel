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
package org.apache.camel.component.websocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.server.Handler;

public class WebsocketEndpoint extends DefaultEndpoint {

    private NodeSynchronization sync;
    private WebsocketStore memoryStore;
    private WebsocketComponent component;
    private SSLContextParameters sslContextParameters;
    private URI uri;
    private List<Handler> handlers;

    private Boolean sendToAll;
    private boolean enableJmx;
    private boolean sessionSupport;
    private boolean crossOriginFilterOn;

    private String remaining;
    private String host;
    private String allowedOrigins;
    // Used to filter CORS
    private String filterPath;

    // Base Resource for the ServletContextHandler
    private String staticResources;

    private Integer port;

    public WebsocketEndpoint(WebsocketComponent component, String uri, String remaining, Map<String, Object> parameters) {
        super(uri, component);
        this.remaining = remaining;
        this.memoryStore = new MemoryWebsocketStore();
        this.sync = new DefaultNodeSynchronization(memoryStore);
        this.component = component;
        try {
            this.uri = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public WebsocketComponent getComponent() {
        ObjectHelper.notNull(component, "component");
        return (WebsocketComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(component, "component");
        WebsocketConsumer consumer = new WebsocketConsumer(this, processor);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WebsocketProducer(this, memoryStore);
    }

    public void connect(WebsocketConsumer consumer) throws Exception {
        component.connect(consumer);
        component.addServlet(sync, consumer, remaining);
        
    }

    public void disconnect(WebsocketConsumer consumer) throws Exception {
        component.disconnect(consumer);
        // Servlet should be removed
    }

    public void connect(WebsocketProducer producer) throws Exception {
        component.connect(producer);
        component.addServlet(sync, producer, remaining);
    }

    public void disconnect(WebsocketProducer producer) throws Exception {
        component.disconnect(producer);
        // Servlet should be removed
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public URI getUri() {
        return uri;
    }

    public Integer getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getStaticResources() {
        return staticResources;
    }

    public void setStaticResources(String staticResources) {
        this.staticResources = staticResources;
    }

    public Boolean getSendToAll() {
        return sendToAll;
    }

    public void setSendToAll(Boolean sendToAll) {
        this.sendToAll = sendToAll;
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public String getPath() {
        return uri.getPath();
    }

    public void setSessionSupport(boolean support) {
        sessionSupport = support;
    }

    public boolean isSessionSupport() {
        return sessionSupport;
    }

    public List<Handler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }


    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isCrossOriginFilterOn() {
        return crossOriginFilterOn;
    }

    public void setCrossOriginFilterOn(boolean crossOriginFilterOn) {
        this.crossOriginFilterOn = crossOriginFilterOn;
    }

    public String getFilterPath() {
        return filterPath;
    }

    public void setFilterPath(String filterPath) {
        this.filterPath = filterPath;
    }


    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(memoryStore);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(memoryStore);
        super.doStop();
    }
}
