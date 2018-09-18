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
package org.apache.camel.component.netty;

import java.math.BigInteger;
import java.security.Principal;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.Timer;

/**
 * Socket level networking using TCP or UDP with the Netty 3.x library.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "netty", title = "Netty", syntax = "netty:protocol:host:port", consumerClass = NettyConsumer.class, label = "networking,tcp,udp")
public class NettyEndpoint extends DefaultEndpoint implements AsyncEndpoint {
    @UriParam
    private NettyConfiguration configuration;
    @UriParam(label = "advanced", javaType = "org.apache.camel.component.netty.NettyServerBootstrapConfiguration",
            description = "To use a custom configured NettyServerBootstrapConfiguration for configuring this endpoint.")
    private Object bootstrapConfiguration; // to include in component docs as NettyServerBootstrapConfiguration is a @UriParams class
    private Timer timer;

    public NettyEndpoint(String endpointUri, NettyComponent component, NettyConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new NettyConsumer(this, processor, configuration);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        Producer answer = new NettyProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    public Exchange createExchange(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        Exchange exchange = createExchange();
        updateMessageHeader(exchange.getIn(), ctx, messageEvent);
        NettyPayloadHelper.setIn(exchange, messageEvent.getMessage());
        return exchange;
    }
    
    public boolean isSingleton() {
        return true;
    }

    @Override
    public NettyComponent getComponent() {
        return (NettyComponent) super.getComponent();
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public Timer getTimer() {
        return timer;
    }

    @Override
    protected String createEndpointUri() {
        ObjectHelper.notNull(configuration, "configuration");
        return "netty:" + getConfiguration().getProtocol() + "://" + getConfiguration().getHost() + ":" + getConfiguration().getPort(); 
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(timer, "timer");
    }
    
    protected SSLSession getSSLSession(ChannelHandlerContext ctx) {
        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        SSLSession sslSession = null;
        if (sslHandler != null) {
            sslSession = sslHandler.getEngine().getSession();
        } 
        return sslSession;
    }

    protected void updateMessageHeader(Message in, ChannelHandlerContext ctx, MessageEvent messageEvent) {
        in.setHeader(NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, ctx);
        in.setHeader(NettyConstants.NETTY_MESSAGE_EVENT, messageEvent);
        in.setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, messageEvent.getRemoteAddress());
        in.setHeader(NettyConstants.NETTY_LOCAL_ADDRESS, messageEvent.getChannel().getLocalAddress());

        if (configuration.isSsl()) {
            // setup the SslSession header
            SSLSession sslSession = getSSLSession(ctx);
            in.setHeader(NettyConstants.NETTY_SSL_SESSION, sslSession);

            // enrich headers with details from the client certificate if option is enabled
            if (configuration.isSslClientCertHeaders()) {
                enrichWithClientCertInformation(sslSession, in);
            }
        }
    }

    /**
     * Enriches the message with client certificate details such as subject name, serial number etc.
     * <p/>
     * If the certificate is unverified then the headers is not enriched.
     *
     * @param sslSession  the SSL session
     * @param message     the message to enrich
     */
    protected void enrichWithClientCertInformation(SSLSession sslSession, Message message) {
        try {
            X509Certificate[] certificates = sslSession.getPeerCertificateChain();
            if (certificates != null && certificates.length > 0) {
                X509Certificate cert = certificates[0];

                Principal subject = cert.getSubjectDN();
                if (subject != null) {
                    message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, subject.getName());
                }
                Principal issuer = cert.getIssuerDN();
                if (issuer != null) {
                    message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME, issuer.getName());
                }
                BigInteger serial = cert.getSerialNumber();
                if (serial != null) {
                    message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, serial.toString());
                }
                message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_NOT_BEFORE, cert.getNotBefore());
                message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_NOT_AFTER, cert.getNotAfter());
            }
        } catch (SSLPeerUnverifiedException e) {
            // ignore
        }
    }

}
