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

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BouncyCastleKemSupportTest {

    private static final Set<String> SHADOWED_BC_TYPES = Set.of(
            "org.bouncycastle.jcajce.spec.KTSParameterSpec",
            "org.bouncycastle.jcajce.spec.KEMGenerateSpec",
            "org.bouncycastle.jcajce.spec.KEMExtractSpec",
            "org.bouncycastle.jcajce.spec.MLKEMParameterSpec",
            "org.bouncycastle.jcajce.SecretKeyWithEncapsulation");

    @BeforeAll
    static void registerProviders() throws Exception {
        BouncyCastleSupport.ensureProviders();
    }

    @Test
    void usesTheStandardJdkKemApiAndPreservesTheSecretKeyCarrierContract() throws Exception {
        Provider provider = BouncyCastleSupport.getProvider("KEM", "ML-KEM");
        assertNotNull(provider.getService("KEM", "ML-KEM"));
        KeyPair keyPair = mlKemKeyPair();

        BouncyCastleKemSupport.EncapsulationResult encapsulated = BouncyCastleKemSupport.encapsulate(
                "ML-KEM", provider, keyPair.getPublic(), "AES", 256, new SecureRandom());
        assertNotNull(encapsulated.secretKey());
        assertTrue(encapsulated.encapsulation().length > 0);
        assertTrue(encapsulated.secretKey().getClass().getName()
                .equals("org.bouncycastle.jcajce.SecretKeyWithEncapsulation"));
        assertArrayEquals(encapsulated.secretKey().getEncoded(),
                BouncyCastleKemSupport.getEncodedKey(encapsulated.secretKey()));
        assertArrayEquals(encapsulated.encapsulation(),
                BouncyCastleKemSupport.getEncapsulation(encapsulated.secretKey()));

        byte[] modifiedCopy = encapsulated.encapsulation();
        modifiedCopy[0] ^= 0x01;
        assertTrue(modifiedCopy[0] != BouncyCastleKemSupport.getEncapsulation(encapsulated.secretKey())[0]);

        SecretKey extracted = BouncyCastleKemSupport.decapsulate(
                "ML-KEM", provider, keyPair.getPrivate(), encapsulated.encapsulation(), "AES", 256,
                new SecureRandom());
        assertArrayEquals(encapsulated.secretKey().getEncoded(), extracted.getEncoded());
    }

    @Test
    void fallsBackToLegacyKeyGeneratorForAlgorithmsWithoutAStandardKemService() throws Exception {
        Provider provider = saberLegacyProvider();
        assertNull(provider.getService("KEM", "SABER"),
                "The selected BC release should exercise SABER through its legacy KeyGenerator service");

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("SABER",
                BouncyCastleSupport.getProviderName("KeyPairGenerator", "SABER"));
        keyPairGenerator.initialize(
                PQCParameterSpecResolver.resolve("SABER", "lightsaberkem128r3"), new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        BouncyCastleKemSupport.EncapsulationResult encapsulated = BouncyCastleKemSupport.encapsulate(
                "SABER", provider, keyPair.getPublic(), "AES", 256, new SecureRandom());
        SecretKey extracted = BouncyCastleKemSupport.decapsulate(
                "SABER", provider, keyPair.getPrivate(), encapsulated.encapsulation(), "AES", 256,
                new SecureRandom());

        assertNotNull(encapsulated.secretKey());
        assertTrue(encapsulated.encapsulation().length > 0);
        assertArrayEquals(encapsulated.secretKey().getEncoded(), extracted.getEncoded());
    }

    @Test
    void resolvesBcParameterSpecsFromTheProviderLoaderRatherThanAShadowingContextLoader() throws Exception {
        Provider provider = BouncyCastleSupport.getProvider("KeyPairGenerator", "ML-KEM");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-KEM", provider);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ShadowingBcClassLoader(original));
        try {
            var spec = PQCParameterSpecResolver.resolve("MLKEM", "ML-KEM-512");
            Class<?> providerView = Class.forName(
                    spec.getClass().getName(), false, provider.getClass().getClassLoader());
            assertSame(providerView, spec.getClass(),
                    "parameter spec must be assignable to the selected provider's API");
            generator.initialize(spec, new SecureRandom());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void resolvesKtsParameterSpecFromTheProviderLoaderRatherThanAShadowingContextLoader() throws Exception {
        Provider provider = BouncyCastleSupport.getProvider("KEM", "ML-KEM");
        KeyPair keyPair = mlKemKeyPair();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ShadowingBcClassLoader(original));
        try {
            BouncyCastleKemSupport.EncapsulationResult encapsulated = BouncyCastleKemSupport.encapsulate(
                    "ML-KEM", provider, keyPair.getPublic(), "AES", 256, new SecureRandom());
            SecretKey extracted = BouncyCastleKemSupport.decapsulate(
                    "ML-KEM", provider, keyPair.getPrivate(), encapsulated.encapsulation(), "AES", 256,
                    new SecureRandom());
            assertArrayEquals(encapsulated.secretKey().getEncoded(), extracted.getEncoded());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void preservesProviderInstancesAlreadyInstalledByAnEmbeddingRuntime() throws Exception {
        Provider bc = Security.getProvider("BC");
        Provider bcpqc = Security.getProvider("BCPQC");
        assertNotNull(bc);
        assertNotNull(bcpqc);

        BouncyCastleSupport.ensureProviders();

        assertSame(bc, Security.getProvider("BC"));
        assertSame(bcpqc, Security.getProvider("BCPQC"));
    }

    @Test
    void explicitlyInjectedKeyGeneratorIsUsedEvenWhenProviderOffersStandardJdkKem() throws Exception {
        Provider provider = java.util.Arrays.stream(Security.getProviders())
                .filter(candidate -> "BC".equals(candidate.getName()) || "BCPQC".equals(candidate.getName()))
                .filter(candidate -> candidate.getService("KEM", "ML-KEM") != null
                        && candidate.getService("KeyGenerator", "ML-KEM") != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "The tested BC baseline must expose both KEM and KeyGenerator services for ML-KEM"));

        KeyPair keyPair = mlKemKeyPair();
        InterleavingProbe encapsulationProbe = new InterleavingProbe();
        KeyGenerator encapsulationGenerator = ProbedKeyGenerator.wrap(
                KeyGenerator.getInstance("ML-KEM", provider), encapsulationProbe);

        encapsulationProbe.holdNextResult();
        BouncyCastleKemSupport.EncapsulationResult encapsulated = BouncyCastleKemSupport.encapsulate(
                encapsulationGenerator, keyPair.getPublic(), "AES", 256, new SecureRandom());
        assertTrue(encapsulationProbe.awaitHolding(5, TimeUnit.SECONDS),
                "the explicitly supplied KeyGenerator must perform generateKey()");

        InterleavingProbe decapsulationProbe = new InterleavingProbe();
        KeyGenerator decapsulationGenerator = ProbedKeyGenerator.wrap(
                KeyGenerator.getInstance("ML-KEM", provider), decapsulationProbe);
        decapsulationProbe.holdNextResult();
        SecretKey extracted = BouncyCastleKemSupport.decapsulate(
                decapsulationGenerator, keyPair.getPrivate(), encapsulated.encapsulation(), "AES", 256,
                new SecureRandom());
        assertTrue(decapsulationProbe.awaitHolding(5, TimeUnit.SECONDS),
                "the explicitly supplied KeyGenerator must perform decapsulation generateKey()");

        assertArrayEquals(encapsulated.secretKey().getEncoded(), extracted.getEncoded());
    }

    private static Provider saberLegacyProvider() throws Exception {
        // Use the real signed BC provider. Exercise the legacy KeyGenerator path only on BC versions
        // where SABER has not gained a standard JDK KEM service.
        Provider provider = BouncyCastleSupport.getProvider("KeyGenerator", "SABER");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                provider.getService("KeyGenerator", "SABER") != null,
                "The selected BC version no longer exposes legacy SABER KeyGenerator");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                provider.getService("KEM", "SABER") == null,
                "SABER now exposes the standard KEM service; its standard API is tested separately");
        return provider;
    }

    private static KeyPair mlKemKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-KEM",
                BouncyCastleSupport.getProviderName("KeyPairGenerator", "ML-KEM"));
        generator.initialize(PQCParameterSpecResolver.resolve("MLKEM", "ML-KEM-512"), new SecureRandom());
        return generator.generateKeyPair();
    }

    private static final class ShadowingBcClassLoader extends ClassLoader {
        ShadowingBcClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!SHADOWED_BC_TYPES.contains(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String resourceName = name.replace('.', '/') + ".class";
                    try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                        if (input == null) {
                            throw new ClassNotFoundException("Cannot read class bytes for " + name);
                        }
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException("Cannot define shadow copy of " + name, e);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
