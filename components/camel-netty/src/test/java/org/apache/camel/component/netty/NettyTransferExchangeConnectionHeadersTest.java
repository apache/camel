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

import java.io.File;
import java.net.InetSocketAddress;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With transferExchange the consumer keeps its own connection headers: the connection it accepted is the source of
 * truth, so the connection headers and properties in the transferred exchange are not used.
 */
@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
public class NettyTransferExchangeConnectionHeadersTest extends BaseNettyTest {

    private static final String CLIENT_CERT_SUBJECT
            = "SERIALNUMBER=1234567890, CN=CommonName, OU=Unit, O=Org, L=Loc, ST=State, C=US";
    private static final String CODECS = "&encoders=#encoder&decoders=#decoder";
    private static final String SSL_OPTIONS = "&ssl=true&passphrase=changeit&keyStoreResource=#ksf&trustStoreResource=#tsf";

    @RegisterExtension
    AvailablePortFinder.Port sslPort = AvailablePortFinder.find();

    @BindToRegistry("ksf")
    public File loadKeystoreKsf() {
        return new File("src/test/resources/keystore.jks");
    }

    @BindToRegistry("tsf")
    public File loadKeystoreTsf() {
        return new File("src/test/resources/keystore.jks");
    }

    @BindToRegistry("encoder")
    public ChannelHandler getEncoder() {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    @BindToRegistry("decoder")
    public ChannelHandler getDecoder() {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    @Test
    public void testConsumerKeepsItsConnectionHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:plain");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("cheese", "gouda");

        Exchange reply = template.request("netty:tcp://localhost:{{port}}?sync=true&transferExchange=true" + CODECS, e -> {
            e.getIn().setBody("Hello World");
            e.getIn().setHeader("cheese", "gouda");
            e.getIn().setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, "10.1.2.3:4567");
            e.getIn().setHeader(NettyConstants.NETTY_LOCAL_ADDRESS, "10.1.2.3:8910");
            e.getIn().setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, "CN=Sender");
            e.getIn().setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, "42");
            e.setProperty(NettyConstants.NETTY_REMOTE_ADDRESS, "10.1.2.3:4567");
        });
        assertNull(reply.getException());

        MockEndpoint.assertIsSatisfied(context);

        Exchange received = mock.getReceivedExchanges().get(0);
        Message in = received.getIn();
        // the addresses of the connection the consumer accepted
        assertInstanceOf(InetSocketAddress.class, in.getHeader(NettyConstants.NETTY_REMOTE_ADDRESS));
        InetSocketAddress local = assertInstanceOf(InetSocketAddress.class, in.getHeader(NettyConstants.NETTY_LOCAL_ADDRESS));
        assertEquals(getPort(), local.getPort());
        // no client certificate on this connection, so no client certificate headers
        assertNull(in.getHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME));
        assertNull(in.getHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO));
        assertNull(received.getProperty(NettyConstants.NETTY_REMOTE_ADDRESS));

        // the producer keeps its own connection headers too: the reply has none from the consumer
        Message message = reply.getMessage();
        assertEquals("Bye World", message.getBody());
        assertEquals("cheddar", message.getHeader("cheese"));
        assertNull(message.getHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME));
        assertNull(message.getHeader(NettyConstants.NETTY_REMOTE_ADDRESS));
    }

    @Test
    public void testConsumerKeepsItsClientCertHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:ssl");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, CLIENT_CERT_SUBJECT);
        mock.expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME, CLIENT_CERT_SUBJECT);
        mock.expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, "1234567890");

        Exchange reply = template.request(
                "netty:tcp://localhost:" + sslPort.getPort() + "?sync=true&transferExchange=true" + CODECS + SSL_OPTIONS, e -> {
                    e.getIn().setBody("Hello World");
                    e.getIn().setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, "CN=Sender");
                    e.getIn().setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME, "CN=Sender CA");
                    e.getIn().setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, "42");
                });
        assertNull(reply.getException());
        assertEquals("Bye World", reply.getMessage().getBody());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true&transferExchange=true" + CODECS)
                        .to("mock:plain")
                        .setHeader("cheese", constant("cheddar"))
                        .setHeader(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME, constant("CN=Consumer"))
                        .transform().constant("Bye World");

                from("netty:tcp://localhost:" + sslPort.getPort() + "?sync=true&transferExchange=true" + CODECS + SSL_OPTIONS
                     + "&needClientAuth=true&sslClientCertHeaders=true")
                        .to("mock:ssl")
                        .transform().constant("Bye World");
            }
        };
    }
}
