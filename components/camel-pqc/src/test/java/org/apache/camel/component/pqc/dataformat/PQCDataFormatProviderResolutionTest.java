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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that PQCDataFormat resolves explicitly configured providers by their advertised JCA services. The test
 * provider deliberately exposes service metadata only: doStart() must validate availability without instantiating the
 * KEM implementation.
 */
class PQCDataFormatProviderResolutionTest {

    @Test
    void acceptsCustomProviderExposingStandardKemWithoutKeyGenerator() throws Exception {
        assertCustomProviderStarts(
                "CamelPqcStandardKemTestProvider", "MLKEM", "KEM", "ML-KEM");
    }

    @Test
    void acceptsCustomProviderExposingLegacyKeyGenerator() throws Exception {
        assertCustomProviderStarts(
                "CamelPqcLegacyKemTestProvider", "BIKE", "KeyGenerator", "BIKE");
    }

    private static void assertCustomProviderStarts(
            String providerName, String camelAlgorithm, String serviceType, String serviceAlgorithm)
            throws Exception {
        Provider provider = new MetadataProvider(providerName, serviceType, serviceAlgorithm);
        int position = Security.addProvider(provider);
        assertTrue(position > 0, "Test provider should be registered: " + providerName);

        try {
            PQCDataFormat dataFormat = new PQCDataFormat();
            dataFormat.setProvider(providerName);
            dataFormat.setKeyEncapsulationAlgorithm(camelAlgorithm);

            // Startup validates the configured provider's KEM or legacy KeyGenerator service.
            // It must not require the provider to be named BC/BCPQC.
            dataFormat.start();
            dataFormat.stop();
        } finally {
            Security.removeProvider(providerName);
        }
    }

    private static final class MetadataProvider extends Provider {

        private MetadataProvider(String name, String serviceType, String algorithm) {
            super(name, "1.0", "Test provider for Camel PQC service resolution");
            putService(new Service(this, serviceType, algorithm, Object.class.getName(), List.of(), Map.of()));
        }
    }
}
