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
package org.apache.camel.component.cometd;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Endpoint for Camel Cometd.
 */
@UriEndpoint(scheme = "cometd,cometds", title = "CometD", syntax = "cometd:protocol:host:port/channelName", consumerClass = CometdConsumer.class, label = "http,websocket")
public class CometdEndpoint extends DefaultEndpoint {

    private CometdComponent component;

    private URI uri;
    @UriPath @Metadata(required = "true")
    private String protocol;
    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private int port;
    @UriPath @Metadata(required = "true")
    private String channelName;
    @UriParam
    private String baseResource;
    @UriParam(defaultValue = "240000")
    private int timeout = 240000;
    @UriParam
    private int interval;
    @UriParam(defaultValue = "30000")
    private int maxInterval = 30000;
    @UriParam(defaultValue = "1500")
    private int multiFrameInterval = 1500;
    @UriParam(defaultValue = "true")
    private boolean jsonCommented = true;
    @UriParam
    private boolean sessionHeadersEnabled;
    @UriParam(defaultValue = "1")
    private int logLevel = 1;
    @UriParam
    private boolean crossOriginFilterOn;
    @UriParam
    private String allowedOrigins;
    @UriParam
    private String filterPath;
    @UriParam(defaultValue = "true")
    private boolean disconnectLocalSession = true;

    public CometdEndpoint(CometdComponent component, String uri, String remaining, Map<String, Object> parameters) {
        super(uri, component);
        this.component = component;
        try {
            this.uri = new URI(uri);
            this.protocol = this.uri.getScheme();
            this.host = this.uri.getHost();
            this.port = this.uri.getPort();
            this.channelName = remaining;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(component, "component");
        CometdProducer producer = new CometdProducer(this);
        return producer;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(component, "component");
        CometdConsumer consumer = new CometdConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public void connect(CometdProducerConsumer prodcons) throws Exception {
        component.connect(prodcons);
    }

    public void disconnect(CometdProducerConsumer prodcons) throws Exception {
        component.disconnect(prodcons);
    }

    public CometdComponent getComponent() {
        return component;
    }

    public boolean isSingleton() {
        return false;
    }

    public String getPath() {
        return uri.getPath();
    }

    public int getPort() {
        if (uri.getPort() == -1) {
            if ("cometds".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return uri.getPort();
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public URI getUri() {
        return uri;
    }

    public String getBaseResource() {
        return baseResource;
    }

    public void setBaseResource(String baseResource) {
        this.baseResource = baseResource;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(int maxInterval) {
        this.maxInterval = maxInterval;
    }

    public int getMultiFrameInterval() {
        return multiFrameInterval;
    }

    public void setMultiFrameInterval(int multiFrameInterval) {
        this.multiFrameInterval = multiFrameInterval;
    }

    public boolean isJsonCommented() {
        return jsonCommented;
    }

    public void setJsonCommented(boolean commented) {
        jsonCommented = commented;
    }
    
    public void setSessionHeadersEnabled(boolean enable) {
        this.sessionHeadersEnabled = enable;
    }

    public boolean areSessionHeadersEnabled() {
        return sessionHeadersEnabled;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
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

    public boolean isDisconnectLocalSession() {
        return disconnectLocalSession;
    }

    public void setDisconnectLocalSession(boolean disconnectLocalSession) {
        this.disconnectLocalSession = disconnectLocalSession;
    }
}
