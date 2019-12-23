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
package org.apache.camel.component.stomp;

import javax.net.ssl.SSLContext;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.SslContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.fusesource.stomp.client.Stomp;
import org.junit.After;
import org.junit.Before;

public abstract class StompBaseTest extends CamelTestSupport {

    protected BrokerService brokerService;
    protected int numberOfMessages = 100;
    protected int port;
    private boolean canTest;
    private SSLContextParameters serverSslContextParameters;
    private SSLContext serverSslContext;
    private SSLContextParameters clientSslContextParameters;
    private SSLContext clientSslContext;

    protected int getPort() {
        return port;
    }

    /**
     * Whether we can test on this box, as not all boxes can be used for reliable CI testing.
     */
    protected boolean canTest() {
        return canTest;
    }

    protected boolean isUseSsl() {
        return false;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        if (isUseSsl()) {
            registry.bind("sslContextParameters", getClientSSLContextParameters());
        }

        return registry;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        try {
            brokerService = new BrokerService();
            brokerService.setPersistent(false);
            brokerService.setAdvisorySupport(false);

            if (isUseSsl()) {
                SslContext sslContext = new SslContext();
                sslContext.setSSLContext(getServerSSLContext());

                brokerService.setSslContext(sslContext);
                brokerService.addConnector("stomp+ssl://localhost:" + getPort() + "?trace=true");
            } else {
                brokerService.addConnector("stomp://localhost:" + getPort() + "?trace=true");
            }

            brokerService.start();
            brokerService.waitUntilStarted();
            super.setUp();
            canTest = true;
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage() + " more details in the log");
            log.warn("Cannot test due " + e.getMessage(), e);
            canTest = false;
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (brokerService != null) {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }
    }

    protected Stomp createStompClient() throws Exception {
        Stomp stomp;
        if (isUseSsl()) {
            stomp = new Stomp("ssl://localhost:" + getPort());
            stomp.setSslContext(getClientSSLContext());
        } else {
            stomp = new Stomp("tcp://localhost:" + getPort());
        }

        return stomp;
    }

    protected SSLContextParameters getServerSSLContextParameters()  {
        if (serverSslContextParameters == null) {
            serverSslContextParameters = getSSLContextParameters("jsse/server.keystore", "password");
        }

        return serverSslContextParameters;
    }

    protected SSLContext getServerSSLContext() throws Exception {
        if (serverSslContext == null) {
            serverSslContext = getServerSSLContextParameters().createSSLContext(context);
        }

        return serverSslContext;
    }

    protected SSLContextParameters getClientSSLContextParameters()  {
        if (clientSslContextParameters == null) {
            clientSslContextParameters = getSSLContextParameters("jsse/client.keystore", "password");
        }

        return clientSslContextParameters;
    }

    protected SSLContext getClientSSLContext() throws Exception {
        if (clientSslContext == null) {
            clientSslContext = getClientSSLContextParameters().createSSLContext(context);
        }

        return clientSslContext;
    }

    private SSLContextParameters getSSLContextParameters(String path, String password)  {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(path);
        ksp.setPassword(password);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(password);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
