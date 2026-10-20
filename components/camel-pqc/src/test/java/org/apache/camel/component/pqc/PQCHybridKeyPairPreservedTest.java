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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.KeyGenerator;

import org.apache.camel.CamelContext;
import org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultECDSAMLDSAMaterial;
import org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultX25519MLKEMMaterial;
import org.apache.camel.impl.DefaultCamelContext;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Auto-configuring the classical half of a hybrid endpoint must not replace PQC key material the route author supplied:
 * signing with a silently substituted key produces signatures nobody can verify against the real key.
 */
public class PQCHybridKeyPairPreservedTest {

    // Per-test: until CAMEL-25795 lands, stopping a context de-registers the providers for the whole JVM
    @BeforeEach
    void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void hybridSignKeepsTheConfiguredPqcKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        kpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair myKeyPair = kpg.generateKeyPair();

        try (CamelContext context = new DefaultCamelContext()) {
            // keyPair and classicalKeyPair are both autowired, so a single KeyPair bean would be injected into both
            // and the classical branch under test would never run
            context.setAutowiredEnabled(false);
            context.getRegistry().bind("myKeyPair", myKeyPair);
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:sign?operation=hybridSign&signatureAlgorithm=MLDSA"
                                                       + "&classicalSignatureAlgorithm=ECDSA_P256&keyPair=#myKeyPair",
                    PQCEndpoint.class);

            assertSame(myKeyPair, endpoint.getConfiguration().getKeyPair(),
                    "the configured PQC key pair must survive hybrid auto-configuration");
            assertNotNull(endpoint.getConfiguration().getClassicalKeyPair(),
                    "the classical half must still be auto-configured");
        }
    }

    @Test
    void hybridKemKeepsTheConfiguredPqcKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        kpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair myKeyPair = kpg.generateKeyPair();
        KeyGenerator myKeyGenerator = KeyGenerator.getInstance("ML-KEM", "BC");

        try (CamelContext context = new DefaultCamelContext()) {
            // keyPair and classicalKeyPair are both autowired, so a single KeyPair bean would be injected into both
            // and the classical branch under test would never run
            context.setAutowiredEnabled(false);
            context.getRegistry().bind("myKeyPair", myKeyPair);
            context.getRegistry().bind("myKeyGenerator", myKeyGenerator);
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:kem?operation=hybridGenerateSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM"
                                                       + "&classicalKEMAlgorithm=X25519&keyPair=#myKeyPair&keyGenerator=#myKeyGenerator",
                    PQCEndpoint.class);

            assertSame(myKeyPair, endpoint.getConfiguration().getKeyPair(),
                    "the configured PQC key pair must survive hybrid auto-configuration");
            assertSame(myKeyGenerator, endpoint.getConfiguration().getKeyGenerator(),
                    "the configured PQC key generator must survive hybrid auto-configuration");
            assertNotNull(endpoint.getConfiguration().getClassicalKeyPair(),
                    "the classical half must still be auto-configured");
        }
    }

    @Test
    void hybridSignStillAutoConfiguresTheMatchedPair() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:sign?operation=hybridSign&signatureAlgorithm=MLDSA&classicalSignatureAlgorithm=ECDSA_P256",
                    PQCEndpoint.class);

            // the matched pair, not the generic MLDSA default: asserting only non-null would not notice the
            // difference, and for the KEM pairing below it is a difference in security level
            assertSame(PQCDefaultECDSAMLDSAMaterial.pqcKeyPair, endpoint.getConfiguration().getKeyPair(),
                    "the PQC half must be the one matched to the classical algorithm");
            assertSame(PQCDefaultECDSAMLDSAMaterial.classicalKeyPair,
                    endpoint.getConfiguration().getClassicalKeyPair(),
                    "the classical half must be auto-configured");
        }
    }

    @Test
    void hybridKemStillAutoConfiguresTheMatchedPair() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:kem?operation=hybridGenerateSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM"
                                                       + "&classicalKEMAlgorithm=X25519",
                    PQCEndpoint.class);

            // PQCDefaultX25519MLKEMMaterial is ML-KEM-768; the generic PQCDefaultMLKEMMaterial is ML-KEM-512, so
            // falling back to it would silently weaken the default hybrid pairing
            assertSame(PQCDefaultX25519MLKEMMaterial.pqcKeyPair, endpoint.getConfiguration().getKeyPair(),
                    "the PQC half must be the ML-KEM-768 material matched to X25519");
            assertSame(PQCDefaultX25519MLKEMMaterial.classicalKeyPair,
                    endpoint.getConfiguration().getClassicalKeyPair(),
                    "the classical half must be auto-configured");
        }
    }
}
