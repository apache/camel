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
package org.apache.camel.component.milo.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiloClientConfigurationTest {

    private static final String ENDPOINT_URI = "opc.tcp://localhost:4840";

    @Test
    void cacheIdIsUnchangedWithoutServerCertificateTrustSettings() {
        assertEquals(ENDPOINT_URI + "|1000.0", configuration().toCacheId());
    }

    @Test
    void endpointsValidatingTheServerDifferentlyDoNotShareAConnection() {
        final MiloClientConfiguration trustAll = configuration();
        trustAll.setTrustAllCertificates(true);
        final MiloClientConfiguration trustList = configuration();
        trustList.setTrustListDirectory("pki");
        final MiloClientConfiguration otherTrustList = configuration();
        otherTrustList.setTrustListDirectory("other-pki");
        final MiloClientConfiguration validator = configuration();
        validator.setCertificateValidator(validator());

        final Set<String> cacheIds = new HashSet<>(
                Arrays.asList(
                        configuration().toCacheId(), trustAll.toCacheId(), trustList.toCacheId(), otherTrustList.toCacheId(),
                        validator.toCacheId()));

        assertEquals(5, cacheIds.size());
    }

    @Test
    void copyAndCloneKeepServerCertificateTrustSettings() {
        final CertificateValidator certificateValidator = validator();
        final MiloClientConfiguration original = configuration();
        original.setTrustListDirectory("pki");
        original.setCertificateValidator(certificateValidator);
        original.setTrustAllCertificates(true);

        for (final MiloClientConfiguration copy : Arrays.asList(new MiloClientConfiguration(original), original.clone())) {
            assertEquals("pki", copy.getTrustListDirectory());
            assertSame(certificateValidator, copy.getCertificateValidator());
            assertTrue(copy.isTrustAllCertificates());
        }
    }

    private static MiloClientConfiguration configuration() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setEndpointUri(ENDPOINT_URI);
        return configuration;
    }

    private static CertificateValidator validator() {
        return (certificateChain, applicationUri, validHostNames) -> {
        };
    }
}
