/*
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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Offers publish/subscribe, peer-to-peer (via a server), and RPC style messaging using the CometD/Bayeux protocol.
 *
 * Using this component in combination with the dojo toolkit library it's possible to push Camel messages directly into
 * the browser using an AJAX based mechanism.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "cometd,cometds", title = "CometD", syntax = "cometd:host:port/channelName",
             category = { Category.NETWORKING, Category.MESSAGING }, headersClass = CometdBinding.class)
public class CometdEndpoint extends DefaultEndpoint {

    private CometdComponent component;

    private URI uri;
    @UriPath(description = "Hostname")
    @Metadata(required = true)
    private String host; // TODO field is reported unread
    @UriPath(description = "Host port number")
    @Metadata(required = true)
    private int port; // TODO field is reported unread
    @UriPath(description = "The channelName represents a topic that can be subscribed to by the Camel endpoints.")
    @Metadata(required = true)
    private String channelName; // TODO field is reported unread
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
    @UriParam(label = "consumer")
    private boolean sessionHeadersEnabled;
    @UriParam(defaultValue = "1", enums = "0,1,2")
    private int logLevel = 1;
    @UriParam
    private boolean crossOriginFilterOn;
    @UriParam(defaultValue = "*")
    private String allowedOrigins;
    @UriParam
    private String filterPath;
    @UriParam(label = "producer")
    private boolean disconnectLocalSession;

    public CometdEndpoint(CometdComponent component, String uri, String remaining) {
        super(uri, component);
        this.component = component;
        try {
            this.uri = new URI(uri);
            this.host = this.uri.getHost();
            this.port = this.uri.getPort();
            this.channelName = remaining;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(component, "component");
        return new CometdProducer(this);
    }

    @Override
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

    @Override
    public CometdComponent getComponent() {
        return component;
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

    /**
     * The root directory for the web resources or classpath. Use the protocol file: or classpath: depending if you want
     * that the component loads the resource from file system or classpath. Classpath is required for OSGI deployment
     * where the resources are packaged in the jar
     */
    public void setBaseResource(String baseResource) {
        this.baseResource = baseResource;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * The server side poll timeout in milliseconds. This is how long the server will hold a reconnect request before
     * responding.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getInterval() {
        return interval;
    }

    /**
     * The client side poll timeout in milliseconds. How long a client will wait between reconnects
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getMaxInterval() {
        return maxInterval;
    }

    /**
     * The max client side poll timeout in milliseconds. A client will be removed if a connection is not received in
     * this time.
     */
    public void setMaxInterval(int maxInterval) {
        this.maxInterval = maxInterval;
    }

    public int getMultiFrameInterval() {
        return multiFrameInterval;
    }

    /**
     * The client side poll timeout, if multiple connections are detected from the same browser.
     */
    public void setMultiFrameInterval(int multiFrameInterval) {
        this.multiFrameInterval = multiFrameInterval;
    }

    public boolean isJsonCommented() {
        return jsonCommented;
    }

    /**
     * If true, the server will accept JSON wrapped in a comment and will generate JSON wrapped in a comment. This is a
     * defence against Ajax Hijacking.
     */
    public void setJsonCommented(boolean commented) {
        jsonCommented = commented;
    }

    /**
     * Whether to include the server session headers in the Camel message when creating a Camel Message for incoming
     * requests.
     */
    public void setSessionHeadersEnabled(boolean enable) {
        this.sessionHeadersEnabled = enable;
    }

    public boolean isSessionHeadersEnabled() {
        return sessionHeadersEnabled;
    }

    public int getLogLevel() {
        return logLevel;
    }

    /**
     * Logging level. 0=none, 1=info, 2=debug.
     */
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * The origins domain that support to cross, if the crosssOriginFilterOn is true
     */
    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isCrossOriginFilterOn() {
        return crossOriginFilterOn;
    }

    /**
     * If true, the server will support for cross-domain filtering
     */
    public void setCrossOriginFilterOn(boolean crossOriginFilterOn) {
        this.crossOriginFilterOn = crossOriginFilterOn;
    }

    public String getFilterPath() {
        return filterPath;
    }

    /**
     * The filterPath will be used by the CrossOriginFilter, if the crosssOriginFilterOn is true
     */
    public void setFilterPath(String filterPath) {
        this.filterPath = filterPath;
    }

    public boolean isDisconnectLocalSession() {
        return disconnectLocalSession;
    }

    /**
     * Whether to disconnect local sessions after publishing a message to its channel. Disconnecting local session is
     * needed as they are not swept by default by CometD, and therefore you can run out of memory.
     */
    public void setDisconnectLocalSession(boolean disconnectLocalSession) {
        this.disconnectLocalSession = disconnectLocalSession;
    }
}
