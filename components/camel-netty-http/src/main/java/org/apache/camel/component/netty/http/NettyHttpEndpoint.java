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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.Map;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyEndpoint;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP server and client using the Netty 4.x.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "netty-http", extendsScheme = "netty", title = "Netty HTTP",
             syntax = "netty-http:protocol://host:port/path", category = { Category.NETWORKING, Category.HTTP },
             lenientProperties = true, headersClass = NettyHttpConstants.class)
@Metadata(excludeProperties = "textline,delimiter,autoAppendDelimiter,decoderMaxLineLength,encoding,allowDefaultCodec,udpConnectionlessSending,networkInterface"
                              + ",clientMode,reconnect,reconnectInterval,useByteBuf,udpByteArrayCodec,broadcast,correlationManager")
public class NettyHttpEndpoint extends NettyEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpEndpoint.class);
    static final String PROXY_NOT_SUPPORTED_MESSAGE = "Netty Http Producer does not support proxy mode";

    @UriParam
    private NettyHttpConfiguration configuration;
    @UriParam(label = "advanced", name = "configuration",
              javaType = "org.apache.camel.component.netty.http.NettyHttpConfiguration",
              description = "To use a custom configured NettyHttpConfiguration for configuring this endpoint.")
    private Object httpConfiguration; // to include in component docs as NettyHttpConfiguration is a @UriParams class
    @UriParam(label = "advanced")
    private NettyHttpBinding nettyHttpBinding;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "consumer,advanced")
    private boolean traceEnabled;
    @UriParam(label = "consumer,advanced")
    private String httpMethodRestrict;
    @UriParam(label = "consumer,advanced")
    private NettySharedHttpServer nettySharedHttpServer;
    @UriParam(label = "consumer,security")
    private NettyHttpSecurityConfiguration securityConfiguration;
    @UriParam(label = "consumer,security", prefix = "securityConfiguration.", multiValue = true)
    private Map<String, Object> securityOptions; // to include in component docs
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;

    public NettyHttpEndpoint(String endpointUri, NettyHttpComponent component, NettyConfiguration configuration) {
        super(endpointUri, component, configuration);
    }

    @Override
    public NettyHttpComponent getComponent() {
        return (NettyHttpComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        NettyHttpConsumer answer = new NettyHttpConsumer(this, processor, getConfiguration());
        configureConsumer(answer);

        if (nettySharedHttpServer != null) {
            answer.setNettyServerBootstrapFactory(nettySharedHttpServer.getServerBootstrapFactory());
            LOG.info("NettyHttpConsumer: {} is using NettySharedHttpServer on port: {}", answer,
                    nettySharedHttpServer.getPort());
        } else {
            // reuse pipeline factory for the same address
            HttpServerBootstrapFactory factory = getComponent().getOrCreateHttpNettyServerBootstrapFactory(answer);
            // force using our server bootstrap factory
            answer.setNettyServerBootstrapFactory(factory);
            LOG.debug("Created NettyHttpConsumer: {} using HttpServerBootstrapFactory: {}", answer, factory);
        }
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (isProxyProtocol()) {
            doFail(new IllegalArgumentException(PROXY_NOT_SUPPORTED_MESSAGE));
        }

        Producer answer = new NettyHttpProducer(this, getConfiguration());
        if (getConfiguration().isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        throw new UnsupportedOperationException("This component does not support polling consumer");
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the HttpProducer
        return true;
    }

    @Override
    public void setConfiguration(NettyConfiguration configuration) {
        super.setConfiguration(configuration);
        this.configuration = (NettyHttpConfiguration) configuration;
    }

    @Override
    public NettyHttpConfiguration getConfiguration() {
        return (NettyHttpConfiguration) super.getConfiguration();
    }

    public NettyHttpBinding getNettyHttpBinding() {
        return nettyHttpBinding;
    }

    /**
     * To use a custom org.apache.camel.component.netty.http.NettyHttpBinding for binding to/from Netty and Camel
     * Message API.
     */
    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter headers.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        getNettyHttpBinding().setHeaderFilterStrategy(headerFilterStrategy);
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * Specifies whether to enable HTTP TRACE for this Netty HTTP consumer. By default TRACE is turned off.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * To disable HTTP methods on the Netty HTTP consumer. You can specify multiple separated by comma.
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public NettySharedHttpServer getNettySharedHttpServer() {
        return nettySharedHttpServer;
    }

    /**
     * To use a shared Netty HTTP server. See Netty HTTP Server Example for more details.
     */
    public void setNettySharedHttpServer(NettySharedHttpServer nettySharedHttpServer) {
        this.nettySharedHttpServer = nettySharedHttpServer;
    }

    public NettyHttpSecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

    /**
     * Refers to a org.apache.camel.component.netty.http.NettyHttpSecurityConfiguration for configuring secure web
     * resources.
     */
    public void setSecurityConfiguration(NettyHttpSecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    public Map<String, Object> getSecurityOptions() {
        return securityOptions;
    }

    /**
     * To configure NettyHttpSecurityConfiguration using key/value pairs from the map
     */
    public void setSecurityOptions(Map<String, Object> securityOptions) {
        this.securityOptions = securityOptions;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        ObjectHelper.notNull(nettyHttpBinding, "nettyHttpBinding", this);
        ObjectHelper.notNull(headerFilterStrategy, "headerFilterStrategy", this);

        if (securityConfiguration != null) {
            StringHelper.notEmpty(securityConfiguration.getRealm(), "realm", securityConfiguration);
            StringHelper.notEmpty(securityConfiguration.getConstraint(), "restricted", securityConfiguration);

            if (securityConfiguration.getSecurityAuthenticator() == null) {
                // setup default JAAS authenticator if none was configured
                JAASSecurityAuthenticator jaas = new JAASSecurityAuthenticator();
                jaas.setName(securityConfiguration.getRealm());
                LOG.info("No SecurityAuthenticator configured, using JAASSecurityAuthenticator as authenticator: {}", jaas);
                securityConfiguration.setSecurityAuthenticator(jaas);
            }
        }
    }

    private boolean isProxyProtocol() {
        URI baseUri = URI.create(getEndpointBaseUri());
        String protocol = baseUri.getScheme();
        return protocol != null && protocol.equalsIgnoreCase("proxy");
    }
}
