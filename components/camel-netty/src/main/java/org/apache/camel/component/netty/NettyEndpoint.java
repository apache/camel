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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * Socket level networking using TCP or UDP with Netty 4.x.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "netty", title = "Netty", syntax = "netty:protocol://host:port",
             category = { Category.NETWORKING }, headersClass = NettyConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   dependsOn = { "encoders", "decoders", "transferExchange" },
                                   byOption = {
                                           @BodyType.When(option = "udpByteArrayCodec", value = "true",
                                                          carriers = Carrier.BYTES, javaType = "byte[]"),
                                           @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                          carriers = Carrier.POJO, javaType = "io.netty.buffer.ByteBuf") },
                                   description = "The data received as a String in the encoding (UTF-8 by default). Over TCP"
                                                 + " the default codec has no framing, so a message may arrive split over"
                                                 + " several exchanges; textline=true gives one line per exchange"),
           consumerReply = @BodyType(value = { Carrier.NULL, Carrier.TEXT },
                                     dependsOn = { "encoders", "decoders", "transferExchange" },
                                     byOption = {
                                             @BodyType.When(option = "textline", value = "true", carriers = Carrier.ANY),
                                             @BodyType.When(option = "udpByteArrayCodec", value = "true",
                                                            carriers = { Carrier.NULL, Carrier.BYTES }),
                                             @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                            carriers = { Carrier.NULL, Carrier.POJO },
                                                            javaType = "io.netty.buffer.ByteBuf") },
                                     description = "With sync=true (the default) the body the route ends with is written"
                                                   + " back: a String or a Netty ByteBuf. Bytes, a stream, a Map or a POJO"
                                                   + " cannot be written and the caller gets no reply: convert it to a String"
                                                   + " first (textline=true converts any body to a line of text). No body"
                                                   + " sends nothing and closes the connection (disconnectOnNoReply)"),
           producerIn = @BodyType(value = { Carrier.NULL, Carrier.TEXT },
                                  dependsOn = { "encoders", "decoders", "transferExchange" },
                                  byOption = {
                                          @BodyType.When(option = "textline", value = "true", carriers = Carrier.ANY),
                                          @BodyType.When(option = "udpByteArrayCodec", value = "true",
                                                         carriers = { Carrier.NULL, Carrier.BYTES }),
                                          @BodyType.When(option = "useByteBuf", value = "true",
                                                         carriers = {
                                                                 Carrier.NULL, Carrier.TEXT, Carrier.BYTES,
                                                                 Carrier.STREAM }),
                                          @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                         carriers = { Carrier.NULL, Carrier.POJO },
                                                         javaType = "io.netty.buffer.ByteBuf") },
                                  description = "The data to send: a String, or a Netty ByteBuf over TCP. Bytes, a stream, a"
                                                + " file, a Map or a POJO fail with an unsupported message type: convert it"
                                                + " to a String first, or use textline=true, which converts any body to a"
                                                + " line of text. No body sends nothing"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL }, javaType = "java.lang.String",
                                   dependsOn = { "encoders", "decoders", "transferExchange" },
                                   byOption = {
                                           @BodyType.When(option = "sync", value = "false", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "udpByteArrayCodec", value = "true",
                                                          carriers = { Carrier.BYTES, Carrier.NULL }, javaType = "byte[]"),
                                           @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.netty.buffer.ByteBuf") },
                                   description = "With sync=true (the default) the reply of the server as a String: over TCP"
                                                 + " without textline, the data of the first read. No body is not sent and"
                                                 + " stays null"))
public class NettyEndpoint extends DefaultEndpoint implements AsyncEndpoint, EndpointServiceLocation {
    @UriParam
    private NettyConfiguration configuration;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;
    @UriParam(label = "consumer,advanced", javaType = "org.apache.camel.component.netty.NettyServerBootstrapConfiguration")
    private NettyServerBootstrapConfiguration bootstrapConfiguration;

    public NettyEndpoint(String endpointUri, NettyComponent component, NettyConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getProtocol() + ":" + configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return configuration.getProtocol();
    }

    public NettyServerBootstrapConfiguration getBootstrapConfiguration() {
        return bootstrapConfiguration;
    }

    /**
     * To use a custom configured NettyServerBootstrapConfiguration for configuring this endpoint. When set via URI
     * (e.g. {@code ?bootstrapConfiguration=#myBean}), its properties are merged into the endpoint configuration by
     * {@code NettyComponent.createEndpoint()}. Note: this parameter is URI-resolved by the component and is not applied
     * when set programmatically via this setter.
     */
    public void setBootstrapConfiguration(NettyServerBootstrapConfiguration bootstrapConfiguration) {
        this.bootstrapConfiguration = bootstrapConfiguration;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
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
        return "netty:" + getConfiguration().getProtocol() + "://" + getConfiguration().getHost() + ":"
               + getConfiguration().getPort();
    }

    protected SSLSession getSSLSession(ChannelHandlerContext ctx) {
        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        SSLSession sslSession = null;
        if (sslHandler != null) {
            sslSession = sslHandler.engine().getSession();
        }
        return sslSession;
    }

    public void updateMessageHeader(Message in, ChannelHandlerContext ctx) {
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
     * @param sslSession the SSL session
     * @param message    the message to enrich
     */
    protected void enrichWithClientCertInformation(SSLSession sslSession, Message message) {
        try {

            Certificate[] certificates = sslSession.getPeerCertificates();
            if (certificates != null && certificates.length > 0) {
                if (!(certificates[0] instanceof X509Certificate)) {
                    return;
                }
                X509Certificate cert = (X509Certificate) certificates[0];

                Principal subject = cert.getSubjectX500Principal();
                if (subject != null) {
                    message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, subject.toString());
                }
                Principal issuer = cert.getIssuerX500Principal();
                if (issuer != null) {
                    message.setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME, issuer.toString());
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
