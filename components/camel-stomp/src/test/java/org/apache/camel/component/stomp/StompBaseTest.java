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
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.fusesource.stomp.client.Stomp;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class StompBaseTest extends CamelTestSupport {

    @RegisterExtension
    public ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .bare()
            .withPersistent(false)
            .withUseJmx(true)
            .withDeleteAllMessagesOnStartup(true)
            .withAdvisorySupport(true)
            .withCustomSetup(this::configureBroker)
            .buildWithRecycle();

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected int numberOfMessages = 100;
    private SSLContextParameters serverSslContextParameters;
    private SSLContext serverSslContext;
    private SSLContextParameters clientSslContextParameters;
    private SSLContext clientSslContext;

    protected boolean isUseSsl() {
        return false;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() {
        SimpleRegistry registry = new SimpleRegistry();
        if (isUseSsl()) {
            registry.bind("sslContextParameters", getClientSSLContextParameters());
        }

        return registry;
    }

    private void configureBroker(BrokerService brokerService) {
        int port = AvailablePortFinder.getNextAvailable();

        if (isUseSsl()) {
            SslContext sslContext = new SslContext();
            try {
                sslContext.setSSLContext(getServerSSLContext());
            } catch (Exception e) {
                fail(e.getMessage());
            }

            brokerService.setSslContext(sslContext);
            try {
                brokerService.addConnector("stomp+ssl://localhost:" + port + "?trace=true");
            } catch (Exception e) {
                fail(e.getMessage());
            }
        } else {
            try {
                brokerService.addConnector("stomp://localhost:" + port + "?trace=true");
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    protected Stomp createStompClient() throws Exception {
        Stomp stomp;

        if (isUseSsl()) {
            stomp = new Stomp("ssl://localhost:" + service.getPort());
            stomp.setSslContext(getClientSSLContext());
        } else {
            stomp = new Stomp("tcp://localhost:" + service.getPort());
        }

        return stomp;
    }

    protected SSLContextParameters getServerSSLContextParameters() {
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

    protected SSLContextParameters getClientSSLContextParameters() {
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

    private SSLContextParameters getSSLContextParameters(String path, String password) {
        // need an early camel context dummy due to ActiveMQEmbeddedService is eager initialized
        CamelContext dummy = new DefaultCamelContext();

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(dummy);
        ksp.setResource(path);
        ksp.setPassword(password);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setCamelContext(dummy);
        kmp.setKeyPassword(password);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setCamelContext(dummy);
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setCamelContext(dummy);
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
