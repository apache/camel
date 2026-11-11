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
package org.apache.camel.component.netty.http.handlers;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.TlsPeerCertificateProvider;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NettyHttpTlsPeerTest {

    @Test
    void obtainsPeerFromChannelPipelineAndRejectsForgedHeaderWithoutTls() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            var certificate = mock(X509Certificate.class);
            var session = mock(SSLSession.class);
            when(session.getPeerCertificates()).thenReturn(new Certificate[] { certificate });
            var engine = mock(SSLEngine.class);
            when(engine.getSession()).thenReturn(session);
            var handler = mock(SslHandler.class);
            when(handler.engine()).thenReturn(engine);
            var pipeline = mock(ChannelPipeline.class);
            when(pipeline.get(SslHandler.class)).thenReturn(handler);
            var channel = mock(ChannelHandlerContext.class);
            when(channel.pipeline()).thenReturn(pipeline);
            HttpServerChannelHandler.populateTlsPeer(exchange, channel);
            var provider = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertArrayEquals(new X509Certificate[] { certificate }, provider.getPeerCertificates());

            when(pipeline.get(SslHandler.class)).thenReturn(null);
            exchange.getMessage().setHeader("CamelNettySSLSession", session);
            HttpServerChannelHandler.populateTlsPeer(exchange, channel);
            var noPeer = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertThrows(SSLPeerUnverifiedException.class, noPeer::getPeerCertificates);
        }
    }

    @Test
    void missingClientAuthenticationIsNotIdentity() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            var session = mock(SSLSession.class);
            when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("No client certificate"));
            var engine = mock(SSLEngine.class);
            when(engine.getSession()).thenReturn(session);
            var handler = mock(SslHandler.class);
            when(handler.engine()).thenReturn(engine);
            var pipeline = mock(ChannelPipeline.class);
            when(pipeline.get(SslHandler.class)).thenReturn(handler);
            var channel = mock(ChannelHandlerContext.class);
            when(channel.pipeline()).thenReturn(pipeline);
            HttpServerChannelHandler.populateTlsPeer(exchange, channel);
            var provider = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertThrows(SSLPeerUnverifiedException.class, provider::getPeerCertificates);
        }
    }
}
