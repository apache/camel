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
package org.apache.camel.component.platform.http;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.platform.http.cookie.CookieConfiguration;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Expose HTTP endpoints using the HTTP server available in the current platform.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "platform-http", title = "Platform HTTP", syntax = "platform-http:path",
             category = { Category.HTTP }, consumerOnly = true)
@Metadata(annotations = {
        "protocol=http",
})
public class PlatformHttpEndpoint extends DefaultEndpoint
        implements AsyncEndpoint, HeaderFilterStrategyAware, EndpointServiceLocation {

    private static final String PROXY_PATH = "proxy";

    @UriPath(description = "The path under which this endpoint serves the HTTP requests, for proxy use 'proxy'")
    @Metadata(required = true)
    private final String path;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether or not the consumer should try to find a target consumer "
                            + "by matching the URI prefix if no exact match is found.")
    private boolean matchOnUriPrefix;
    @UriParam(label = "consumer", description = "A comma separated list of HTTP methods to serve, e.g. GET,POST ."
                                                + " If no methods are specified, all methods will be served.")
    private String httpMethodRestrict;
    @UriParam(label = "consumer", description = "The content type this endpoint accepts as an input, such as"
                                                + " application/xml or application/json. <code>null</code> or <code>&#42;/&#42;</code> mean no restriction.")
    private String consumes;
    @UriParam(label = "consumer", description = "The content type this endpoint produces, such as"
                                                + " application/xml or application/json.")
    private String produces;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "If enabled and an Exchange failed processing on the consumer side the response's body won't contain the exception's stack trace.")
    private boolean muteException = true;
    @UriParam(label = "consumer,advanced", description = "A comma or whitespace separated list of file extensions."
                                                         + " Uploads having these extensions will be stored locally."
                                                         + " Null value or asterisk (*) will allow all files.")
    private String fileNameExtWhitelist;
    @UriParam(label = "advanced", description = "An HTTP Server engine implementation to serve the requests of this"
                                                + " endpoint.")
    private PlatformHttpEngine platformHttpEngine;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter headers to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam(label = "advanced,consumer",
              description = "Whether to use streaming for large requests and responses (currently only supported by camel-platform-http-vertx)")
    private boolean useStreaming;
    @UriParam(label = "advanced,consumer", description = "The properties set on a Cookies when a Cookie is added via the"
                                                         + " Cookie Handler (currently only supported by camel-platform-http-vertx)")
    private CookieConfiguration cookieConfiguration = new CookieConfiguration();
    @UriParam(label = "advanced,consumer",
              description = "Whether to enable the Cookie Handler that allows Cookie addition, expiry, and retrieval"
                            + " (currently only supported by camel-platform-http-vertx)")
    private boolean useCookieHandler;

    public PlatformHttpEndpoint(String uri, String remaining, Component component) {
        super(uri, component);
        path = remaining;
    }

    @Override
    public PlatformHttpComponent getComponent() {
        return (PlatformHttpComponent) super.getComponent();
    }

    @Override
    public String getServiceUrl() {
        String server = "http://0.0.0.0";
        int port = getOrCreateEngine().getServerPort();
        if (port > 0) {
            server += ":" + port;
        }
        return server;
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer is not supported");
    }

    @Override
    public DefaultPlatformHttpConsumer createConsumer(Processor processor) throws Exception {
        DefaultPlatformHttpConsumer consumer = new DefaultPlatformHttpConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    protected PlatformHttpConsumer createPlatformHttpConsumer(Processor processor) throws Exception {
        PlatformHttpConsumer consumer = getOrCreateEngine().createConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public String getPath() {
        return isHttpProxy() ? "/" : path;
    }

    public PlatformHttpEngine getPlatformHttpEngine() {
        return platformHttpEngine;
    }

    public void setPlatformHttpEngine(PlatformHttpEngine platformHttpEngine) {
        this.platformHttpEngine = platformHttpEngine;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public String getFileNameExtWhitelist() {
        return fileNameExtWhitelist;
    }

    public void setFileNameExtWhitelist(String fileNameExtWhitelist) {
        this.fileNameExtWhitelist = fileNameExtWhitelist;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public boolean isMuteException() {
        return muteException;
    }

    public void setMuteException(boolean muteException) {
        this.muteException = muteException;
    }

    public boolean isUseStreaming() {
        return useStreaming;
    }

    public void setUseStreaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
    }

    public CookieConfiguration getCookieConfiguration() {
        return cookieConfiguration;
    }

    public void setCookieConfiguration(CookieConfiguration cookieConfiguration) {
        this.cookieConfiguration = cookieConfiguration;
    }

    public boolean isUseCookieHandler() {
        return useCookieHandler;
    }

    public void setUseCookieHandler(boolean useCookieHandler) {
        this.useCookieHandler = useCookieHandler;
    }

    PlatformHttpEngine getOrCreateEngine() {
        return platformHttpEngine != null
                ? platformHttpEngine
                : getComponent().getOrCreateEngine();
    }

    public boolean isHttpProxy() {
        return this.path.startsWith(PROXY_PATH);
    }
}
