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

import java.io.IOException;
import java.net.*;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class XmppServerContainer extends GenericContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServerContainer.class);

    private static final String CONTAINER_NAME = "vysper-wrapper";
    private static final String VYSPER_IMAGE = "5mattho/vysper-wrapper:0.3";
    private static final Integer PORT_DEFAULT = 5222;
    private static final Integer PORT_REST = 8080;

    public XmppServerContainer() {
        super(VYSPER_IMAGE);
        setWaitStrategy(Wait.forListeningPort());
        withExposedPorts(PORT_DEFAULT, PORT_REST);
        withNetworkAliases(CONTAINER_NAME);
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        waitingFor(Wait.forLogMessage(".*Started Application in.*", 1));
    }

    public String getUrl() {
        return String.format("%s:%d", this.getContainerIpAddress(), this.getMappedPort(PORT_DEFAULT));
    }

    public void bindSSLContextTo(Registry registry) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");

        keyStore.load(ObjectHelper.loadResourceAsStream("bogus_mina_tls.cert"), "boguspw".toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        ConnectionConfiguration connectionConfig = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom("apache.camel"))
                .setHostAddress(InetAddress.getByName(this.getContainerIpAddress()))
                .setPort(this.getMappedPort(PORT_DEFAULT))
                //                .setHostAddress(InetAddress.getLocalHost())
                //                .setPort(PORT_DEFAULT)
                .setCustomSSLContext(sslContext)
                .setHostnameVerifier((hostname, session) -> true)
                .build();

        registry.bind("customConnectionConfig", connectionConfig);
    }

    public void stopXmppEndpoint() throws IOException {
        get("stop-tcp");
    }

    public void startXmppEndpoint() throws IOException {
        get("start-tcp");
    }

    private void get(String urlAppendix) throws IOException {
        URL url = new URL(
                String.format("http://%s:%d/%s", this.getContainerIpAddress(), getMappedPort(PORT_REST), urlAppendix));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.getInputStream();
        conn.disconnect();
    }
}
