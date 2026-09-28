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
package org.apache.camel.component.kafka;

import java.util.Properties;

import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaSslContextParametersTest {

    private static SSLContextParameters sslContextParameters() {
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("file:/global/trust.p12");
        keyStore.setType("PKCS12");
        keyStore.setPassword("global-password");
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(keyStore);
        SSLContextParameters scp = new SSLContextParameters();
        scp.setTrustManagers(trustManagers);
        return scp;
    }

    @Test
    public void testSslContextParametersUseSsl() {
        KafkaConfiguration kcfg = new KafkaConfiguration();
        kcfg.setSslContextParameters(sslContextParameters());

        for (Properties props : new Properties[] { kcfg.createProducerProperties(), kcfg.createConsumerProperties() }) {
            assertEquals("SSL", props.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
            assertEquals("/global/trust.p12", props.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            // the type from the sslContextParameters is not replaced by the default of the endpoint option
            assertEquals("PKCS12", props.getProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
        }
    }

    @Test
    public void testEndpointOptionsAppliedAfterSslContextParameters() {
        KafkaConfiguration kcfg = new KafkaConfiguration();
        kcfg.setSslContextParameters(sslContextParameters());
        kcfg.setSslTruststoreLocation("/strict/endpoint-trust.p12");
        kcfg.setSslTruststorePassword("endpoint-password");

        for (Properties props : new Properties[] { kcfg.createProducerProperties(), kcfg.createConsumerProperties() }) {
            assertEquals("/strict/endpoint-trust.p12", props.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            assertEquals("endpoint-password", props.getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            assertEquals("https", props.getProperty(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG));
        }
    }

    @Test
    public void testExplicitSecurityProtocolKept() {
        KafkaConfiguration kcfg = new KafkaConfiguration();
        kcfg.setSslContextParameters(sslContextParameters());
        kcfg.setSecurityProtocol("SASL_SSL");

        assertEquals("SASL_SSL", kcfg.createProducerProperties().getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
    }
}
