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
package org.apache.camel.component.pqc;

import java.security.Provider;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BouncyCastleSupportTest {

    @Test
    void selectsProviderByRequestedJcaService() {
        Provider keyPairGeneratorProvider = new TestProvider("BC", "KeyPairGenerator", "ML-DSA");
        Provider signatureProvider = new TestProvider("BCPQC", "Signature", "ML-DSA");

        assertSame(signatureProvider,
                BouncyCastleSupport.findProvider("Signature", "ML-DSA", keyPairGeneratorProvider, signatureProvider));
        assertSame(keyPairGeneratorProvider,
                BouncyCastleSupport.findProvider("KeyPairGenerator", "ML-DSA", keyPairGeneratorProvider,
                        signatureProvider));
    }

    @Test
    void doesNotSelectProviderWhichOnlyHasAnotherService() {
        Provider keyPairGeneratorProvider = new TestProvider("BC", "KeyPairGenerator", "ML-DSA");
        assertNull(BouncyCastleSupport.findProvider("Signature", "ML-DSA", keyPairGeneratorProvider));
    }

    @Test
    void resolvesCipherTransformationAgainstBaseCipherService() {
        Provider aesProvider = new TestProvider("BC", "Cipher", "AES");

        assertSame(aesProvider,
                BouncyCastleSupport.findProvider("Cipher", "AES/GCM/NoPadding", aesProvider));
        assertSame(aesProvider,
                BouncyCastleSupport.findProvider("Cipher", "AES/CCM/NoPadding", aesProvider));
    }

    private static final class TestProvider extends Provider {
        private static final long serialVersionUID = 1L;

        TestProvider(String name, String serviceType, String algorithm) {
            super(name, "1.0", "Test provider for service-specific BC lookup");
            putService(new Service(
                    this, serviceType, algorithm, "java.lang.Object", Collections.emptyList(),
                    Map.<String, String> of()));
        }
    }
}
