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
package org.apache.camel.component.pqc.dataformat;

import java.security.Provider;
import java.security.Security;
import java.util.List;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests provider service discovery without pretending that an unsigned test-classes directory is a trusted JCE
 * provider. Actual KEM operations are exercised by BouncyCastleKemSupportTest and PQCDataFormatTest using the real,
 * signed Bouncy Castle provider artifacts.
 */
class PQCDataFormatProviderTest {

    @Test
    void startsWithConfiguredProviderThatOffersJdkKemWithoutLegacyKeyGenerator() throws Exception {
        Provider bcKemProvider = BouncyCastleSupport.getProvider("KEM", "ML-KEM");
        Provider.Service kemService = bcKemProvider.getService("KEM", "ML-KEM");
        assertNotNull(kemService);

        // This provider exists only to test metadata/startup validation. JCE rejects a cryptographic Provider
        // implementation loaded from target/test-classes because it is not a trusted signed provider JAR.
        // Never pass this test provider to KEM.getInstance() or KeyGenerator.getInstance().
        Provider metadataProvider = new DelegatingProvider("CAMEL-PQC-DATAFORMAT-METADATA", List.of(kemService));
        assertNotNull(metadataProvider.getService("KEM", "ML-KEM"));
        assertNull(metadataProvider.getService("KeyGenerator", "ML-KEM"));

        Security.removeProvider(metadataProvider.getName());
        assertTrue(Security.addProvider(metadataProvider) > 0);

        PQCDataFormat dataFormat = new PQCDataFormat();
        dataFormat.setProvider(metadataProvider.getName());
        try {
            dataFormat.start();
            assertTrue(dataFormat.isStarted());
        } finally {
            if (dataFormat.isStarted()) {
                dataFormat.stop();
            }
            Security.removeProvider(metadataProvider.getName());
        }
    }

    @Test
    void rejectsConfiguredProviderWithoutKemOrLegacyKeyGeneratorService() throws Exception {
        Provider metadataProvider = new DelegatingProvider("CAMEL-PQC-DATAFORMAT-EMPTY", List.of());
        Security.removeProvider(metadataProvider.getName());
        assertTrue(Security.addProvider(metadataProvider) > 0);

        PQCDataFormat dataFormat = new PQCDataFormat();
        dataFormat.setProvider(metadataProvider.getName());
        try {
            RuntimeCamelException failure = assertThrows(RuntimeCamelException.class, dataFormat::start);
            java.security.NoSuchAlgorithmException cause = assertInstanceOf(
                    java.security.NoSuchAlgorithmException.class, failure.getCause());
            assertTrue(cause.getMessage().contains("advertises neither KEM nor KeyGenerator"),
                    "The startup failure should explain that the configured provider lacks both KEM services");
        } finally {
            if (dataFormat.isStarted()) {
                dataFormat.stop();
            }
            Security.removeProvider(metadataProvider.getName());
        }
    }

    private static final class DelegatingProvider extends Provider {
        private static final long serialVersionUID = 1L;

        DelegatingProvider(String name, List<Provider.Service> services) {
            super(name, "1.0", "Test-only provider metadata facade; never used for cryptographic execution");
            for (Provider.Service service : services) {
                Map<String, String> attributes = new java.util.HashMap<>();
                for (String attribute : List.of(
                        "SupportedModes", "SupportedPaddings", "SupportedKeyFormats", "SupportedKeyClasses")) {
                    String value = service.getAttribute(attribute);
                    if (value != null) {
                        attributes.put(attribute, value);
                    }
                }
                putService(new Service(
                        this, service.getType(), service.getAlgorithm(), service.getClassName(), List.of(), attributes));
            }
        }
    }
}
