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
package org.apache.camel.component.ahc.ws;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled("Not yet migrated to work with Jetty 9 - and this component is deprecated CAMEL-17667")
public class WssProducerTest extends WsProducerTestBase {
    protected static final String PW = "changeit";
    private final JettyConfiguration jettyConfiguration = JettyConfigurationBuilder
            .emptyTemplate()
            .withPort(AvailablePortFinder.getNextAvailable())
            .withContextPath(JettyConfiguration.ROOT_CONTEXT_PATH)
            .withServletConfiguration()
            .addServletConfiguration(new JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<>(
                    TestServletFactory.class.getName(),
                    JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration.ROOT_PATH_SPEC))
            .build()
            .withSslContext(() -> {
                try {
                    return WssProducerTest.defineSSLContextServerParameters().createSSLContext(null);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .build();
    @RegisterExtension
    public JettyEmbeddedService service = new JettyEmbeddedService(jettyConfiguration);

    @Override
    protected String getTargetURL() {
        return "ahc-wss://localhost:" + service.getPort();
    }

    @Override
    protected void setUpComponent() {
        WsComponent wsComponent = (WsComponent) camelContext.getComponent("ahc-wss");
        wsComponent.setSslContextParameters(defineSSLContextClientParameters());
    }

    private static SSLContextParameters defineSSLContextServerParameters() {

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.p12");
        ksp.setPassword(PW);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(PW);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        // NOTE: Needed since the client uses a loose trust configuration when no ssl context
        // is provided.  We turn on WANT client-auth to prefer using authentication
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(ClientAuthentication.WANT.name());

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);

        return sslContextParameters;
    }

    private static SSLContextParameters defineSSLContextClientParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.p12");
        ksp.setPassword(PW);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
