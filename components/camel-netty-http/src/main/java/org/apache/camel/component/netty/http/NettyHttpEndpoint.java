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
package org.apache.camel.component.netty.http;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP based {@link NettyEndpoint}
 */
@UriEndpoint(scheme = "netty-http", title = "Netty HTTP", syntax = "netty-http:host:port/path", consumerClass = NettyHttpConsumer.class, label = "http")
public class NettyHttpEndpoint extends NettyEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpEndpoint.class);
    private NettyHttpBinding nettyHttpBinding;
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam
    private NettyHttpConfiguration configuration;
    @UriParam
    private boolean traceEnabled;
    @UriParam
    private String httpMethodRestrict;
    private NettySharedHttpServer nettySharedHttpServer;
    private NettyHttpSecurityConfiguration securityConfiguration;

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
            LOG.info("NettyHttpConsumer: {} is using NettySharedHttpServer on port: {}", answer, nettySharedHttpServer.getPort());
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
        Producer answer = new NettyHttpProducer(this, getConfiguration());
        if (isSynchronous()) {
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
    public Exchange createExchange(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        Exchange exchange = createExchange();

        // use the http binding
        HttpRequest request = (HttpRequest) messageEvent.getMessage();
        Message in = getNettyHttpBinding().toCamelMessage(request, exchange, getConfiguration());
        exchange.setIn(in);
        
        // setup the common message headers 
        updateMessageHeader(in, ctx, messageEvent);

        // honor the character encoding
        String contentType = in.getHeader(Exchange.CONTENT_TYPE, String.class);
        String charset = NettyHttpHelper.getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
            in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        return exchange;
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

    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        getNettyHttpBinding().setHeaderFilterStrategy(headerFilterStrategy);
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public NettySharedHttpServer getNettySharedHttpServer() {
        return nettySharedHttpServer;
    }

    public void setNettySharedHttpServer(NettySharedHttpServer nettySharedHttpServer) {
        this.nettySharedHttpServer = nettySharedHttpServer;
    }

    public NettyHttpSecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

    public void setSecurityConfiguration(NettyHttpSecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(nettyHttpBinding, "nettyHttpBinding", this);
        ObjectHelper.notNull(headerFilterStrategy, "headerFilterStrategy", this);

        if (securityConfiguration != null) {
            ObjectHelper.notEmpty(securityConfiguration.getRealm(), "realm", securityConfiguration);
            ObjectHelper.notEmpty(securityConfiguration.getConstraint(), "restricted", securityConfiguration);

            if (securityConfiguration.getSecurityAuthenticator() == null) {
                // setup default JAAS authenticator if none was configured
                JAASSecurityAuthenticator jaas = new JAASSecurityAuthenticator();
                jaas.setName(securityConfiguration.getRealm());
                LOG.info("No SecurityAuthenticator configured, using JAASSecurityAuthenticator as authenticator: {}", jaas);
                securityConfiguration.setSecurityAuthenticator(jaas);
            }
        }
    }
}
