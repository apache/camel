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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.AlgorithmParameterSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BouncyCastlePqcCompatibilityTest {

    @Test
    void createsLmsHssAndXmssParameterSpecsFromRuntimeApi() throws Exception {
        assertSupportedSpec(BouncyCastlePqcCompatibility.lmsKeyGenParameterSpec(
                "lms_sha256_n32_h5", "sha256_n32_w1"), "LMSKeyGenParameterSpec");
        assertSupportedSpec(BouncyCastlePqcCompatibility.lmsHssKeyGenParameterSpec(
                new String[] { "lms_sha256_n32_h5", "lms_sha256_n32_h5" },
                new String[] { "sha256_n32_w2", "sha256_n32_w2" }),
                "LMSHSSKeyGenParameterSpec");
        assertSupportedSpec(BouncyCastlePqcCompatibility.xmssParameterSpec(2, "SHA256"), "XMSSParameterSpec");
        assertSupportedSpec(BouncyCastlePqcCompatibility.xmssMtParameterSpec(20, 2, "SHA256"),
                "XMSSMTParameterSpec");
    }

    @Test
    void readsStatefulXmssPrivateKeyWithoutLinkingToBcInterface() throws Exception {
        BouncyCastleSupport.ensureProviders();
        String keyPairGeneratorProvider = BouncyCastleSupport.getProviderName("KeyPairGenerator", "XMSS");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("XMSS", keyPairGeneratorProvider);
        keyPairGenerator.initialize(BouncyCastlePqcCompatibility.xmssParameterSpec(2, "SHA256"), new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        assertEquals(0, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey));
        assertEquals(4, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey));

        String signatureProvider = BouncyCastleSupport.getProviderName("Signature", "XMSS");
        Signature signature = Signature.getInstance("XMSS", signatureProvider);
        signature.initSign(privateKey);
        signature.update("adapter-test".getBytes(StandardCharsets.UTF_8));
        assertNotNull(signature.sign());

        assertEquals(1, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey));
        assertEquals(3, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey));
    }

    @Test
    void nonStatefulPrivateKeysUseExistingFallbackValues() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        PrivateKey privateKey = keyPairGenerator.generateKeyPair().getPrivate();

        assertEquals(0, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey));
        assertEquals(-1, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey));
    }

    @Test
    void prefersRuntimeContextLoaderAndRetainsComponentFallback() {
        ClassLoader runtimeLoader = new ClassLoader() {
        };
        ClassLoader componentLoader = new ClassLoader() {
        };

        assertArrayEquals(new ClassLoader[] { runtimeLoader, componentLoader },
                BouncyCastlePqcCompatibility.compatibilityClassLoaders(runtimeLoader, componentLoader));
        assertArrayEquals(new ClassLoader[] { runtimeLoader },
                BouncyCastlePqcCompatibility.compatibilityClassLoaders(runtimeLoader, runtimeLoader));
        assertArrayEquals(new ClassLoader[] { componentLoader },
                BouncyCastlePqcCompatibility.compatibilityClassLoaders(null, componentLoader));
        assertArrayEquals(new ClassLoader[0],
                BouncyCastlePqcCompatibility.compatibilityClassLoaders(null, null));
    }

    @Test
    void fallsBackToTheComponentLoaderWhenTheRuntimeContextLoaderCannotSeeBcClasses() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
            assertSupportedSpec(BouncyCastlePqcCompatibility.xmssParameterSpec(2, "SHA256"), "XMSSParameterSpec");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static void assertSupportedSpec(AlgorithmParameterSpec spec, String simpleName) throws Exception {
        assertNotNull(spec);
        assertInstanceOf(AlgorithmParameterSpec.class, spec);

        String currentType = "org.bouncycastle.jcajce.spec." + simpleName;
        String legacyType = "org.bouncycastle.pqc.jcajce.spec." + simpleName;
        boolean currentTypeAvailable;
        try {
            Class.forName(currentType, false, spec.getClass().getClassLoader());
            currentTypeAvailable = true;
        } catch (ClassNotFoundException e) {
            currentTypeAvailable = false;
        }
        assertEquals(currentTypeAvailable ? currentType : legacyType, spec.getClass().getName(),
                "The adapter should prefer the non-deprecated package when it is available");
    }
}
