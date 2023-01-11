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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.artemis.services.ArtemisEmbeddedServiceBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.fusesource.stomp.client.Stomp;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class StompBaseTest extends CamelTestSupport {

    protected int numberOfMessages = 100;
    int sslServicePort = AvailablePortFinder.getNextAvailable();
    int servicePort = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public ArtemisService service = new ArtemisEmbeddedServiceBuilder()
            .withCustomConfiguration(configuration -> {
                try {
                    configuration.setJMXManagementEnabled(true);

                    configuration.addAcceptorConfiguration("stomp-ssl-acceptor",
                            String.format("tcp://0.0.0.0:%s?" +
                                          "sslEnabled=true;" +
                                          "keyStorePath=jsse/server-side-keystore.jks;" +
                                          "keyStorePassword=password;" +
                                          "protocols=STOMP",
                                    sslServicePort));

                    configuration.addAcceptorConfiguration("stomp-tcp-acceptor",
                            String.format("tcp://0.0.0.0:%s?protocols=STOMP",
                                    servicePort));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .build();

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

    protected Stomp createStompClient() throws Exception {
        Stomp stomp;

        if (isUseSsl()) {
            stomp = new Stomp("ssl://localhost:" + sslServicePort);
            stomp.setSslContext(getClientSSLContext());
        } else {
            stomp = new Stomp("tcp://localhost:" + servicePort);
        }

        return stomp;
    }

    protected SSLContextParameters getClientSSLContextParameters() {
        if (clientSslContextParameters == null) {
            clientSslContextParameters
                    = getSSLContextParameters("jsse/client-side-keystore.jks", "jsse/client-side-truststore.jks", "password");
        }

        return clientSslContextParameters;
    }

    protected SSLContext getClientSSLContext() throws Exception {
        if (clientSslContext == null) {
            clientSslContext = getClientSSLContextParameters().createSSLContext(context);
        }

        return clientSslContext;
    }

    private SSLContextParameters getSSLContextParameters(String keyStore, String trustStore, String password) {
        // need an early camel context dummy due to ActiveMQEmbeddedService is eager initialized
        CamelContext dummy = new DefaultCamelContext();

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(dummy);
        ksp.setResource(keyStore);
        ksp.setPassword(password);

        KeyStoreParameters tsp = new KeyStoreParameters();
        tsp.setCamelContext(dummy);
        tsp.setResource(trustStore);
        tsp.setPassword(password);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(password);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(tsp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setCamelContext(dummy);
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
