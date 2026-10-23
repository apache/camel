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
package org.apache.camel.component.vertx.common;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.TCPSSLOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustAllTrustManager;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxHelperTest {

    private CamelContext context;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testStandaloneCustomTrustManagerApplied() throws Exception {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        TrustManagersParameters trustManagers = new TrustManagersParameters();

        X509TrustManager customTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Custom reject");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Custom reject");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };

        trustManagers.setTrustManager(customTrustManager);
        sslContextParameters.setTrustManagers(trustManagers);

        TCPSSLOptions options = new NetClientOptions();
        VertxHelper.setupSSLOptions(context, sslContextParameters, options);

        assertThat(options.isSsl()).isTrue();
        assertThat(options.getTrustOptions()).isNotNull();
        assertThat(options.getTrustOptions().getTrustManagerFactory(null).getTrustManagers())
                .containsExactly(customTrustManager);
    }

    @Test
    public void testTrustAllTrustManagerApplied() throws Exception {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setTrustManager(TrustAllTrustManager.INSTANCE);
        sslContextParameters.setTrustManagers(trustManagers);

        TCPSSLOptions options = new NetClientOptions();
        VertxHelper.setupSSLOptions(context, sslContextParameters, options);

        assertThat(options.isSsl()).isTrue();
        assertThat(options.getTrustOptions()).isNotNull();
        assertThat(options.getTrustOptions().getTrustManagerFactory(null).getTrustManagers())
                .containsExactly(TrustAllTrustManager.INSTANCE);
    }

    @Test
    public void testKeyStoreTrustManagerFactoryApplied() throws Exception {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("jsse/truststore.jks");
        keyStore.setPassword("storepass");
        trustManagers.setKeyStore(keyStore);
        sslContextParameters.setTrustManagers(trustManagers);

        TCPSSLOptions options = new NetClientOptions();
        VertxHelper.setupSSLOptions(context, sslContextParameters, options);

        assertThat(options.isSsl()).isTrue();
        assertThat(options.getTrustOptions()).isNotNull();
        assertThat(options.getTrustOptions()).isInstanceOf(TrustManagerFactoryOptions.class);
    }

    @Test
    public void testNoTrustOptionsWhenNoneConfigured() throws Exception {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        TCPSSLOptions options = new NetClientOptions();
        VertxHelper.setupSSLOptions(context, sslContextParameters, options);

        assertThat(options.isSsl()).isTrue();
        assertThat(options.getTrustOptions()).isNull();
    }
}
