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
package org.apache.camel.dsl.yaml;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SSLContextParametersTest extends YamlTestSupport {

    @Test
    void loadSslContextParameters() throws Exception {
        loadRoutesNoValidate("""
                - sslContextParameters:
                    id: mySSL
                    keyStore: server.p12
                    keystorePassword: changeit
                    trustStore: truststore.p12
                    trustStorePassword: changeit
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
                """);

        // verify SSL context parameters were registered in registry
        SSLContextParameters sslParams = context.getRegistry().lookupByNameAndType("mySSL", SSLContextParameters.class);
        assertThat(sslParams).isNotNull();

        // verify it was also set as the global default
        assertThat(context.getSSLContextParameters()).isNotNull();

        // verify key store configuration
        assertThat(sslParams.getKeyManagers()).isNotNull();
        assertThat(sslParams.getKeyManagers().getKeyStore()).isNotNull();
        assertThat(sslParams.getKeyManagers().getKeyStore().getResource()).isEqualTo("server.p12");
        assertThat(sslParams.getKeyManagers().getKeyPassword()).isEqualTo("changeit");

        // verify trust store configuration
        assertThat(sslParams.getTrustManagers()).isNotNull();
        assertThat(sslParams.getTrustManagers().getKeyStore()).isNotNull();
        assertThat(sslParams.getTrustManagers().getKeyStore().getResource()).isEqualTo("truststore.p12");
        assertThat(sslParams.getTrustManagers().getKeyStore().getPassword()).isEqualTo("changeit");
    }

    @Test
    void loadSslContextParametersWithAdvancedOptions() throws Exception {
        loadRoutesNoValidate("""
                - sslContextParameters:
                    id: myAdvancedSSL
                    keyStore: server.p12
                    keystorePassword: changeit
                    secureSocketProtocol: TLSv1.3
                    certAlias: myAlias
                    clientAuthentication: WANT
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
                """);

        SSLContextParameters sslParams = context.getRegistry().lookupByNameAndType("myAdvancedSSL",
                SSLContextParameters.class);
        assertThat(sslParams).isNotNull();
        assertThat(sslParams.getSecureSocketProtocol()).isEqualTo("TLSv1.3");
        assertThat(sslParams.getCertAlias()).isEqualTo("myAlias");
        assertThat(sslParams.getServerParameters()).isNotNull();
        assertThat(sslParams.getServerParameters().getClientAuthentication()).isEqualTo("WANT");
    }

    @Test
    void loadSslContextParametersWithTrustAllCertificates() throws Exception {
        loadRoutesNoValidate("""
                - sslContextParameters:
                    id: myTrustAllSSL
                    trustAllCertificates: "true"
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
                """);

        SSLContextParameters sslParams = context.getRegistry().lookupByNameAndType("myTrustAllSSL",
                SSLContextParameters.class);
        assertThat(sslParams).isNotNull();
        // trust managers should be configured with trust-all
        assertThat(sslParams.getTrustManagers()).isNotNull();
        // key managers should be null since no keyStore was provided
        assertThat(sslParams.getKeyManagers()).isNull();
    }

    @Test
    void loadSslContextParametersWithoutIdSetsGlobalDefault() throws Exception {
        loadRoutesNoValidate("""
                - sslContextParameters:
                    keyStore: server.p12
                    keystorePassword: changeit
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
                """);

        // verify global default is set even without id
        assertThat(context.getSSLContextParameters()).isNotNull();
        assertThat(context.getSSLContextParameters().getKeyManagers()).isNotNull();
        assertThat(context.getSSLContextParameters().getKeyManagers().getKeyStore().getResource()).isEqualTo("server.p12");
    }
}
