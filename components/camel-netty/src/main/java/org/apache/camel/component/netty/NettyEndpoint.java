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
package org.apache.camel.component.netty;

import java.math.BigInteger;
import java.security.Principal;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * Socket level networking using TCP or UDP with the Netty 4.x library.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "netty", title = "Netty", syntax = "netty:protocol:host:port", label = "networking,tcp,udp")
public class NettyEndpoint extends DefaultEndpoint implements AsyncEndpoint {
    @UriParam
    private NettyConfiguration configuration;

    public NettyEndpoint(String endpointUri, NettyComponent component, NettyConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new NettyConsumer(this, processor, configuration);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer answer = new NettyProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    public Exchange createExchange(ChannelHandlerContext ctx, Object message) throws Exception {
        Exchange exchange = createExchange();
        updateMessageHeader(exchange.getIn(), ctx);
        NettyPayloadHelper.setIn(exchange, message);
        return exchange;
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

    @Override
    protected String createEndpointUri() {
        ObjectHelper.notNull(configuration, "configuration");
        return "netty:" + getConfiguration().getProtocol() + "://" + getConfiguration().getHost() + ":" + getConfiguration().getPort();
    }

    protected SSLSession getSSLSession(ChannelHandlerContext ctx) {
        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        SSLSession sslSession = null;
        if (sslHandler != null) {
            sslSession = sslHandler.engine().getSession();
        }
        return sslSession;
    }

    protected void updateMessageHeader(Message in, ChannelHandlerContext ctx) {
        in.setHeader(NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, ctx);
        in.setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, ctx.channel().remoteAddress());
        in.setHeader(NettyConstants.NETTY_LOCAL_ADDRESS, ctx.channel().localAddress());

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
