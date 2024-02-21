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

import java.net.InetAddress;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;

public final class XmppTestUtil {

    private XmppTestUtil() {

    }

    public static void bindSSLContextTo(Registry registry, String hostAddress, int port) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");

        keyStore.load(ObjectHelper.loadResourceAsStream("bogus_mina_tls.cer"), "boguspw".toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        ConnectionConfiguration connectionConfig = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom("apache.camel"))
                .setHostAddress(InetAddress.getByName(hostAddress))
                .setPort(port)
                .setCustomSSLContext(sslContext)
                .setHostnameVerifier((hostname, session) -> true)
                .build();

        registry.bind("customConnectionConfig", connectionConfig);
    }
}
