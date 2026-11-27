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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.NamedGroupsParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SignatureSchemesParameters;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Kafka client builds its TLS connections from the ssl.* properties, which cannot carry the named groups and the
 * signature schemes of sslContextParameters. The configuration says so with a WARN, once per endpoint, instead of
 * dropping them in silence.
 */
class KafkaClientConfigurationSslTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger(KafkaClientConfiguration.class);
    private final String thread = Thread.currentThread().getName();
    private final AbstractAppender appender
            = new AbstractAppender("KafkaClientConfigurationSslTest", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    // only the events of this test, as other tests may run at the same time
                    if (event.getLevel().isMoreSpecificThan(Level.WARN) && thread.equals(event.getThreadName())) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    @BeforeEach
    void captureWarnings() {
        // an appender added while another test still starts log4j would be dropped with the old configuration
        await().atMost(30, TimeUnit.SECONDS).until(logger.getContext()::isStarted);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapture() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void namedGroupsAndSignatureSchemesAreReportedOncePerEndpoint() {
        SSLContextParameters scp = new SSLContextParameters();
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(List.of("secp384r1"));
        scp.setNamedGroups(ngp);
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        ssp.setSignatureScheme(List.of("ecdsa_secp384r1_sha384"));
        scp.setSignatureSchemes(ssp);
        scp.setSecureSocketProtocol("TLSv1.3");

        TestConfiguration configuration = new TestConfiguration();
        configuration.setSslContextParameters(scp);

        // the properties of a client are created at each start of a client, and once for each consumer
        Properties props = configuration.createProperties();
        configuration.createProperties();
        configuration.createProperties();

        // the settings that the ssl.* properties can carry are still mapped
        assertEquals("TLSv1.3", props.get(SslConfigs.SSL_PROTOCOL_CONFIG));
        assertEquals(1, warnings.size(), "a single warning: " + warnings);
        assertTrue(warnings.get(0).contains("namedGroups, signatureSchemes"), warnings.get(0));

        // an endpoint gets a copy of the configuration of the component, which warns on its own
        configuration.copy().createProperties();
        assertEquals(2, warnings.size(), "a warning for the copy: " + warnings);
    }

    @Test
    void noWarningWithoutNamedGroupsOrSignatureSchemes() {
        SSLContextParameters scp = new SSLContextParameters();
        CipherSuitesParameters csp = new CipherSuitesParameters();
        csp.setCipherSuite(List.of("TLS_AES_256_GCM_SHA384"));
        scp.setCipherSuites(csp);

        TestConfiguration configuration = new TestConfiguration();
        configuration.setSslContextParameters(scp);

        Properties props = configuration.createProperties();

        assertEquals("TLS_AES_256_GCM_SHA384", props.get(SslConfigs.SSL_CIPHER_SUITES_CONFIG));
        assertFalse(props.isEmpty());
        assertTrue(warnings.isEmpty(), "no warning: " + warnings);
    }

    private static final class TestConfiguration extends KafkaClientConfiguration {

        Properties createProperties() {
            Properties props = new Properties();
            applySecurityProperties(props);
            return props;
        }

        @Override
        protected TestConfiguration copy() {
            return (TestConfiguration) super.copy();
        }
    }
}
