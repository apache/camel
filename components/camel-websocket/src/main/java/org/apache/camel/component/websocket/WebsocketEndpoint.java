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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.server.Handler;

/**
 * The websocket component provides websocket endpoints for communicating with clients using websocket.
 *
 * This component uses Jetty as the websocket implementation.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "websocket", title = "Jetty Websocket", syntax = "websocket:host:port/resourceUri", consumerClass = WebsocketConsumer.class, label = "websocket")
public class WebsocketEndpoint extends DefaultEndpoint {

    private WebsocketComponent component;
    private URI uri;
    private List<Handler> handlers;

    @UriPath(defaultValue = "0.0.0.0")
    private String host;
    @UriPath(defaultValue = "9292")
    private Integer port;
    @UriPath @Metadata(required = "true")
    private String resourceUri;

    @UriParam(label = "producer")
    private Boolean sendToAll;
    @UriParam(label = "producer", defaultValue = "30000")
    private Integer sendTimeout = 30000;
    @UriParam(label = "monitoring")
    private boolean enableJmx;
    @UriParam(label = "consumer")
    private boolean sessionSupport;
    @UriParam(label = "cors")
    private boolean crossOriginFilterOn;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "cors")
    private String allowedOrigins;
    @UriParam(label = "cors")
    private String filterPath;
    @UriParam(label = "consumer")
    private String staticResources;
    @UriParam(label = "advanced", defaultValue = "8192")
    private Integer bufferSize;
    @UriParam(label = "advanced", defaultValue = "300000")
    private Integer maxIdleTime;
    @UriParam(label = "advanced")
    private Integer maxTextMessageSize;
    @UriParam(defaultValue = "-1")
    private Integer maxBinaryMessageSize;
    @UriParam(label = "advanced", defaultValue = "13")
    private Integer minVersion;

    public WebsocketEndpoint(WebsocketComponent component, String uri, String resourceUri, Map<String, Object> parameters) {
        super(uri, component);
        this.resourceUri = resourceUri;
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
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WebsocketProducer(this);
    }

    public void connect(WebsocketConsumer consumer) throws Exception {
        component.connect(consumer);
    }

    public void disconnect(WebsocketConsumer consumer) throws Exception {
        component.disconnect(consumer);
    }

    public void connect(WebsocketProducer producer) throws Exception {
        component.connect(producer);
    }

    public void disconnect(WebsocketProducer producer) throws Exception {
        component.disconnect(producer);
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

    /**
     * The hostname. The default value is <tt>0.0.0.0</tt>.
     * Setting this option on the component will use the component configured value as default.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The port number. The default value is <tt>9292</tt>.
     * Setting this option on the component will use the component configured value as default.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getStaticResources() {
        return staticResources;
    }

    /**
     * Set a resource path for static resources (such as .html files etc).
     * <p/>
     * The resources can be loaded from classpath, if you prefix with <tt>classpath:</tt>,
     * otherwise the resources is loaded from file system or from JAR files.
     * <p/>
     * For example to load from root classpath use <tt>classpath:.</tt>, or
     * <tt>classpath:WEB-INF/static</tt>
     * <p/>
     * If not configured (eg <tt>null</tt>) then no static resource is in use.
     */
    public void setStaticResources(String staticResources) {
        this.staticResources = staticResources;
    }

    public Boolean getSendToAll() {
        return sendToAll;
    }

    /**
     * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of having to use the WebsocketConstants.SEND_TO_ALL header on the message.
     */
    public void setSendToAll(Boolean sendToAll) {
        this.sendToAll = sendToAll;
    }

    public Integer getSendTimeout() {
        return sendTimeout;
    }

    /**
     * Timeout in millis when sending to a websocket channel.
     * The default timeout is 30000 (30 seconds).
     */
    public void setSendTimeout(Integer sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public String getPath() {
        return uri.getPath();
    }

    /**
     * Whether to enable session support which enables HttpSession for each http request.
     */
    public void setSessionSupport(boolean support) {
        sessionSupport = support;
    }

    public boolean isSessionSupport() {
        return sessionSupport;
    }

    public Integer getBufferSize() {
        return bufferSize;
    }

    /**
     * Set the buffer size of the websocketServlet, which is also the max frame byte size (default 8192)
     */
    public void setBufferSize(Integer bufferSize) {
        this.bufferSize = bufferSize;
    }

    public Integer getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Set the time in ms that the websocket created by the websocketServlet may be idle before closing. (default is 300000)
     */
    public void setMaxIdleTime(Integer maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public Integer getMaxTextMessageSize() {
        return maxTextMessageSize;
    }

    /**
     * Can be used to set the size in characters that the websocket created by the websocketServlet may be accept before closing.
     */
    public void setMaxTextMessageSize(Integer maxTextMessageSize) {
        this.maxTextMessageSize = maxTextMessageSize;
    }

    public Integer getMaxBinaryMessageSize() {
        return maxBinaryMessageSize;
    }

    /**
     * Can be used to set the size in bytes that the websocket created by the websocketServlet may be accept before closing. (Default is -1 - or unlimited)
     */
    public void setMaxBinaryMessageSize(Integer maxBinaryMessageSize) {
        this.maxBinaryMessageSize = maxBinaryMessageSize;
    }

    public Integer getMinVersion() {
        return minVersion;
    }

    /**
     * Can be used to set the minimum protocol version accepted for the websocketServlet. (Default 13 - the RFC6455 version)
     */
    public void setMinVersion(Integer minVersion) {
        this.minVersion = minVersion;
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

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    /**
     * If this option is true, Jetty JMX support will be enabled for this endpoint. See Jetty JMX support for more details.
     */
    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * The CORS allowed origins. Use * to allow all.
     */
    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isCrossOriginFilterOn() {
        return crossOriginFilterOn;
    }

    /**
     * Whether to enable CORS
     */
    public void setCrossOriginFilterOn(boolean crossOriginFilterOn) {
        this.crossOriginFilterOn = crossOriginFilterOn;
    }

    public String getFilterPath() {
        return filterPath;
    }

    /**
     * Context path for filtering CORS
     */
    public void setFilterPath(String filterPath) {
        this.filterPath = filterPath;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * Name of the websocket channel to use
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }
}
