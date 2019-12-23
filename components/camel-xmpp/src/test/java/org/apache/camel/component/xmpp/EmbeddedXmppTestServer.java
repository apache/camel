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
package org.apache.camel.component.xmpp;

import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.ObjectHelper;
import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.authorization.Anonymous;
import org.apache.vysper.xmpp.authorization.SASLMechanism;
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.MUCModule;
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.Conference;
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.RoomType;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;

public final class EmbeddedXmppTestServer {

    private XMPPServer xmppServer;
    private TCPEndpoint endpoint;
    private int port;

    public EmbeddedXmppTestServer() { 
        initializeXmppServer();
    }

    private void initializeXmppServer() {
        try {
            xmppServer = new XMPPServer("apache.camel");

            StorageProviderRegistry providerRegistry = new MemoryStorageProviderRegistry();
            AccountManagement accountManagement = (AccountManagement) providerRegistry.retrieve(AccountManagement.class);

            Entity user = EntityImpl.parseUnchecked("camel_consumer@apache.camel");
            accountManagement.addUser(user, "secret");

            Entity user2 = EntityImpl.parseUnchecked("camel_producer@apache.camel");
            accountManagement.addUser(user2, "secret");
            
            Entity user3 = EntityImpl.parseUnchecked("camel_producer1@apache.camel");
            accountManagement.addUser(user3, "secret");

            xmppServer.setStorageProviderRegistry(providerRegistry);

            endpoint = new TCPEndpoint();
            this.port = AvailablePortFinder.getNextAvailable();
            endpoint.setPort(port);

            xmppServer.addEndpoint(endpoint);

            InputStream stream = ObjectHelper.loadResourceAsStream("xmppServer.jks");
            xmppServer.setTLSCertificateInfo(stream, "secret");

            // allow anonymous logins
            xmppServer.setSASLMechanisms(Arrays.asList(new SASLMechanism[]{new Anonymous()}));

            xmppServer.start();

            // add the multi-user chat module and create a few test rooms
            Conference conference = new Conference("test conference");
            conference.createRoom(EntityImpl.parseUnchecked("camel-anon@apache.camel"), "camel-anon", RoomType.FullyAnonymous);
            conference.createRoom(EntityImpl.parseUnchecked("camel-test@apache.camel"), "camel-test", RoomType.Public);
            xmppServer.addModule(new MUCModule("conference", conference));
        } catch (Exception e) {
            throw new RuntimeException("An error occurred when initializing the XMPP Test Server.", e);
        }
    }

    public void startXmppEndpoint() throws Exception {
        endpoint.start();
    }

    public void stopXmppEndpoint() {
        endpoint.stop();
    }

    public int getXmppPort() {
        return port;
    }

    public void bindSSLContextTo(Registry registry) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(ObjectHelper.loadResourceAsStream("xmppServer.jks"), "secret".toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

        ConnectionConfiguration connectionConfig = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom("apache.camel"))
                .setHostAddress(InetAddress.getLocalHost())
                .setPort(getXmppPort())
                .setCustomSSLContext(sslContext)
                .setHostnameVerifier((hostname, session) -> true)
                .build();

        registry.bind("customConnectionConfig", connectionConfig);
    }

    public void stop() {
        xmppServer.stop();
    }
}
