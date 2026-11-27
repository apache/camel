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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

import javax.crypto.KeyGenerator;

import org.apache.camel.component.pqc.crypto.kem.PQCDefaultHQCMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultMLKEMMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRULPRimeMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRUMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultSNTRUPrimeMaterial;
import org.apache.camel.util.SecureRandomHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the actual BC JCA KEM APIs used by camel-pqc.
 *
 * HQC 1.87 implements the 2026-10-12 HQC specification, whose keys and ciphertexts are not compatible with earlier BC
 * releases. Consequently, this class deliberately checks an HQC round trip within the active runtime; it does not claim
 * cross-release HQC key/ciphertext interoperability.
 *
 * BC 1.87 also changes implicit-rejection handling for NTRU LPRime and SNTRUPrime. On 1.87 the rejection secret must be
 * bound to the re-encrypted ciphertext: two different modified ciphertexts must not collapse to the same rejection
 * secret. On 1.86 the test still checks that a malformed ciphertext does not recover the valid shared secret, but does
 * not assert the 1.87-specific ciphertext-binding fix.
 */
class PQCBCVersionBehaviorTest {

    @BeforeAll
    static void registerProviders() throws Exception {
        BouncyCastleSupport.ensureProviders();
    }

    @Test
    void activeProviderVersionMatchesTheRuntimeMatrixLeg() {
        String expectedVersion = System.getProperty("camel.pqc.expectedBcVersion");
        if (expectedVersion == null || expectedVersion.isBlank()) {
            return;
        }

        Provider bc = Security.getProvider("BC");
        Provider bcpqc = Security.getProvider("BCPQC");
        assertNotNull(bc, "BC provider must be installed");
        assertNotNull(bcpqc, "BCPQC provider must be installed");
        assertTrue(bc.getVersionStr().startsWith(expectedVersion),
                () -> "Expected BC " + expectedVersion + " but runtime loaded " + bc.getVersionStr());
        assertTrue(bcpqc.getVersionStr().startsWith(expectedVersion),
                () -> "Expected BCPQC " + expectedVersion + " but runtime loaded " + bcpqc.getVersionStr());
    }

    @Test
    void hqcEncapsulationRoundTripsWithinTheActiveProviderVersion() throws Exception {
        KeyPair keyPair = PQCDefaultHQCMaterial.getKeyPair();
        PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.HQC;
        Encapsulation encapsulation = encapsulate(algorithm, keyPair);

        byte[] recovered = decapsulate(algorithm, keyPair, encapsulation.ciphertext());
        assertArrayEquals(encapsulation.secret(), recovered,
                "HQC must round-trip with the key/ciphertext format of the active BC runtime");
    }

    /**
     * Writes real JCA KEM vectors for an independent BC runtime. The Python runner invokes this method once under 1.86
     * and once under 1.87, then feeds each vector file to the opposite runtime.
     */
    @Test
    void exportCrossVersionKemVectors() throws Exception {
        String exportLocation = System.getProperty("camel.pqc.interop.export");
        if (exportLocation == null || exportLocation.isBlank()) {
            return;
        }

        Properties vectors = new Properties();
        exportVector(vectors, "hqc", PQCKeyEncapsulationAlgorithms.HQC, PQCDefaultHQCMaterial.getKeyPair());
        exportVector(vectors, "mlkem", PQCKeyEncapsulationAlgorithms.MLKEM, PQCDefaultMLKEMMaterial.getKeyPair());
        exportVector(vectors, "ntru", PQCKeyEncapsulationAlgorithms.NTRU, PQCDefaultNTRUMaterial.getKeyPair());
        exportVector(vectors, "ntrulpr", PQCKeyEncapsulationAlgorithms.NTRULPRime,
                PQCDefaultNTRULPRimeMaterial.getKeyPair());
        exportVector(vectors, "sntrupr", PQCKeyEncapsulationAlgorithms.SNTRUPrime,
                PQCDefaultSNTRUPrimeMaterial.getKeyPair());

        Path path = Path.of(exportLocation).toAbsolutePath();
        Files.createDirectories(path.getParent());
        try (OutputStream output = Files.newOutputStream(path)) {
            vectors.store(output, "CAMEL-27541 BC KEM cross-version vectors");
        }
    }

    /**
     * Verifies real vectors produced by the other BC release. NTRU, NTRU LPRime and SNTRUPrime key/ciphertext encodings
     * remain decodable across 1.86/1.87, but their derived AES keys differ because the provider-side KDF behavior
     * changed. HQC is expected not to interoperate because BC 1.87 changed its specification.
     */
    @Test
    void verifyCrossVersionKemVectors() throws Exception {
        String importLocation = System.getProperty("camel.pqc.interop.import");
        if (importLocation == null || importLocation.isBlank()) {
            return;
        }

        Properties vectors = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(importLocation))) {
            vectors.load(input);
        }

        // Compare NTRU-family default-KDF outputs separately from the HQC key/ciphertext format.
        verifyChangedKdfVector(vectors, "ntru", PQCKeyEncapsulationAlgorithms.NTRU);
        verifyChangedKdfVector(vectors, "ntrulpr", PQCKeyEncapsulationAlgorithms.NTRULPRime);
        verifyChangedKdfVector(vectors, "sntrupr", PQCKeyEncapsulationAlgorithms.SNTRUPrime);

        // Re-import each producer public key under the consumer BC runtime, encapsulate to it, and decapsulate
        // with the matching private key. This exercises X.509 public-key compatibility in both matrix directions.
        verifyPublicKeyVector(vectors, "mlkem", PQCKeyEncapsulationAlgorithms.MLKEM);
        verifyPublicKeyVector(vectors, "ntru", PQCKeyEncapsulationAlgorithms.NTRU);
        verifyPublicKeyVector(vectors, "ntrulpr", PQCKeyEncapsulationAlgorithms.NTRULPRime);
        verifyPublicKeyVector(vectors, "sntrupr", PQCKeyEncapsulationAlgorithms.SNTRUPrime);
        verifyPublicKeyVector(vectors, "hqc", PQCKeyEncapsulationAlgorithms.HQC);

        boolean hqcCompatible = tryVerifyVector(vectors, "hqc", PQCKeyEncapsulationAlgorithms.HQC);
        String producerVersion = vectors.getProperty("hqc.providerVersion");
        String providerName = PQCKeyEncapsulationAlgorithms.HQC.getBcProvider("KeyGenerator");
        Provider consumerProvider = Security.getProvider(providerName);
        assertNotNull(consumerProvider, "HQC provider must be registered: " + providerName);
        String consumerVersion = consumerProvider.getVersionStr();
        String explicitExpectation = System.getProperty("camel.pqc.interop.expectHqcCompatible");
        // BC 1.87 introduced the new HQC specification. Old-old and new-new pairs should interoperate;
        // crossing that format boundary should not. A system property permits explicit testing overrides.
        boolean expected = isAtLeast187(producerVersion) == isAtLeast187(consumerVersion);
        if (explicitExpectation != null && !explicitExpectation.isBlank()) {
            expected = Boolean.parseBoolean(explicitExpectation);
        }
        assertTrue(hqcCompatible == expected,
                "HQC cross-version compatibility changed across the BC 1.87 format boundary. producer="
                                              + producerVersion + ", consumer=" + consumerVersion);
    }

    @Test
    void ntruFamilyKdfBehaviorMatchesTheRuntimeVersion() throws Exception {
        for (PQCKeyEncapsulationAlgorithms algorithm : new PQCKeyEncapsulationAlgorithms[] {
                PQCKeyEncapsulationAlgorithms.NTRU,
                PQCKeyEncapsulationAlgorithms.NTRULPRime,
                PQCKeyEncapsulationAlgorithms.SNTRUPrime }) {
            KeyPair pair = keyPairFor(algorithm);
            Encapsulation encapsulation = encapsulate(algorithm, pair);
            byte[] defaultDerivedKey = decapsulate(algorithm, pair, encapsulation.ciphertext());
            byte[] noKdfKey = decapsulateWithoutKdf(algorithm, pair.getPrivate(), encapsulation.ciphertext());
            assertArrayEquals(encapsulation.secret(), defaultDerivedKey,
                    algorithm + " default encapsulation/extraction must agree on this runtime");
            String providerName = algorithm.getBcProvider("KeyGenerator");
            Provider provider = Security.getProvider(providerName);
            assertNotNull(provider, "KEM provider must be registered: " + providerName);
            if (isAtLeast187(provider.getVersionStr())) {
                assertFalse(MessageDigest.isEqual(defaultDerivedKey, noKdfKey),
                        algorithm + " BC 1.87 must apply the default KDF-3/SHA-256");
            } else {
                assertArrayEquals(defaultDerivedKey, noKdfKey,
                        algorithm + " BC 1.86 baseline ignores the KDF spec in this JCA KEM service");
            }
        }
    }

    @ParameterizedTest(name = "{0}: valid encapsulation recovers the same secret")
    @EnumSource(value = PQCKeyEncapsulationAlgorithms.class, names = { "NTRU", "NTRULPRime", "SNTRUPrime" })
    void ntruFamilyValidEncapsulationsRecoverTheSameSecret(PQCKeyEncapsulationAlgorithms algorithm) throws Exception {
        KeyPair keyPair = keyPairFor(algorithm);
        Encapsulation encapsulation = encapsulate(algorithm, keyPair);

        byte[] recovered = decapsulate(algorithm, keyPair, encapsulation.ciphertext());
        assertArrayEquals(encapsulation.secret(), recovered,
                algorithm + " valid KEM ciphertext must recover the encapsulated shared secret");
    }

    @ParameterizedTest(name = "{0}: implicit-rejection secret is bound to the received ciphertext")
    @EnumSource(value = PQCKeyEncapsulationAlgorithms.class, names = { "NTRULPRime", "SNTRUPrime" })
    void ntruPrimeImplicitRejectionSecretIsBoundToReceivedCiphertext(PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        KeyPair keyPair = keyPairFor(algorithm);
        Encapsulation encapsulation = encapsulate(algorithm, keyPair);
        byte[][] malformedCiphertexts = malleableCiphertextTwins(encapsulation.ciphertext());

        byte[] rejectedA = decapsulate(algorithm, keyPair, malformedCiphertexts[0]);
        byte[] rejectedB = decapsulate(algorithm, keyPair, malformedCiphertexts[1]);

        assertFalse(MessageDigest.isEqual(encapsulation.secret(), rejectedA),
                algorithm + " must not return the valid shared secret for a malformed ciphertext");
        assertFalse(MessageDigest.isEqual(encapsulation.secret(), rejectedB),
                algorithm + " must not return the valid shared secret for a malformed ciphertext");

        String providerName = algorithm.getBcProvider("KeyGenerator");
        Provider provider = Security.getProvider(providerName);
        assertNotNull(provider, "KEM provider must be registered: " + providerName);
        if (isAtLeast187(provider.getVersionStr())) {
            assertFalse(MessageDigest.isEqual(rejectedA, rejectedB),
                    algorithm + " BC 1.87 must bind rejection output to the received ciphertext");
        } else {
            assertArrayEquals(rejectedA, rejectedB,
                    algorithm + " BC 1.86 baseline: these two encodings decrypt to the same candidate message");
        }
    }

    private static KeyPair keyPairFor(PQCKeyEncapsulationAlgorithms algorithm) throws Exception {
        return switch (algorithm) {
            case NTRU -> PQCDefaultNTRUMaterial.getKeyPair();
            case NTRULPRime -> PQCDefaultNTRULPRimeMaterial.getKeyPair();
            case SNTRUPrime -> PQCDefaultSNTRUPrimeMaterial.getKeyPair();
            default -> throw new IllegalArgumentException("Unsupported KEM test algorithm: " + algorithm);
        };
    }

    private static Encapsulation encapsulate(PQCKeyEncapsulationAlgorithms algorithm, KeyPair keyPair)
            throws Exception {
        return encapsulate(algorithm, keyPair.getPublic());
    }

    private static Encapsulation encapsulate(PQCKeyEncapsulationAlgorithms algorithm, PublicKey publicKey)
            throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(
                algorithm.getAlgorithm(), algorithm.getBcProvider("KeyGenerator"));
        generator.init(new KEMGenerateSpec(publicKey, "AES", 256), SecureRandomHelper.getSecureRandom());
        SecretKeyWithEncapsulation result = (SecretKeyWithEncapsulation) generator.generateKey();
        return new Encapsulation(result.getEncapsulation().clone(), result.getEncoded().clone());
    }

    private static byte[] decapsulate(
            PQCKeyEncapsulationAlgorithms algorithm, KeyPair keyPair, byte[] ciphertext)
            throws Exception {
        return decapsulate(algorithm, keyPair.getPrivate(), ciphertext);
    }

    private static byte[] decapsulate(
            PQCKeyEncapsulationAlgorithms algorithm, PrivateKey privateKey, byte[] ciphertext)
            throws Exception {
        KeyGenerator extractor = KeyGenerator.getInstance(
                algorithm.getAlgorithm(), algorithm.getBcProvider("KeyGenerator"));
        extractor.init(new KEMExtractSpec(privateKey, ciphertext, "AES", 256), SecureRandomHelper.getSecureRandom());
        SecretKeyWithEncapsulation result = (SecretKeyWithEncapsulation) extractor.generateKey();
        return result.getEncoded().clone();
    }

    private static byte[] decapsulateWithoutKdf(
            PQCKeyEncapsulationAlgorithms algorithm, PrivateKey privateKey, byte[] ciphertext)
            throws Exception {
        KeyGenerator extractor = KeyGenerator.getInstance(
                algorithm.getAlgorithm(), algorithm.getBcProvider("KeyGenerator"));
        KEMExtractSpec noKdfSpec
                = new KEMExtractSpec.Builder(privateKey, ciphertext, "AES", 256).withNoKdf().build();
        extractor.init(noKdfSpec, SecureRandomHelper.getSecureRandom());
        SecretKeyWithEncapsulation result = (SecretKeyWithEncapsulation) extractor.generateKey();
        return result.getEncoded().clone();
    }

    /**
     * Both NTRU LPRime and SNTRU Prime ciphertexts append a 32-byte confirmation hash after the encoded polynomial.
     * Changing only the final hash byte leaves the decoded polynomial/candidate message unchanged, while producing two
     * distinct malformed received ciphertexts. BC 1.86 hashes the re-encrypted candidate ciphertext on rejection, so
     * these twins produce the same rejection secret. BC 1.87 hashes the received ciphertext, so the secrets differ.
     */
    private static byte[][] malleableCiphertextTwins(byte[] ciphertext) {
        if (ciphertext.length < 32) {
            throw new IllegalArgumentException("NTRU Prime ciphertext is shorter than its 32-byte confirmation hash");
        }
        byte[] first = Arrays.copyOf(ciphertext, ciphertext.length);
        byte[] second = Arrays.copyOf(ciphertext, ciphertext.length);
        int finalHashByte = ciphertext.length - 1;
        first[finalHashByte] ^= 0x01;
        second[finalHashByte] ^= 0x02;
        return new byte[][] { first, second };
    }

    private static void exportVector(
            Properties vectors, String prefix, PQCKeyEncapsulationAlgorithms algorithm,
            KeyPair keyPair)
            throws Exception {
        Encapsulation encapsulation = encapsulate(algorithm, keyPair);
        vectors.setProperty(prefix + ".providerVersion",
                Security.getProvider(algorithm.getBcProvider("KeyGenerator")).getVersionStr());
        vectors.setProperty(prefix + ".privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        vectors.setProperty(prefix + ".publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        vectors.setProperty(prefix + ".ciphertext", Base64.getEncoder().encodeToString(encapsulation.ciphertext()));
        vectors.setProperty(prefix + ".secret", Base64.getEncoder().encodeToString(encapsulation.secret()));
    }

    private static void verifyChangedKdfVector(
            Properties vectors, String prefix,
            PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        String producerVersion = vectors.getProperty(prefix + ".providerVersion");
        String providerName = algorithm.getBcProvider("KeyGenerator");
        Provider consumerProvider = Security.getProvider(providerName);
        assertNotNull(consumerProvider, "KEM provider must be registered: " + providerName);
        String consumerVersion = consumerProvider.getVersionStr();

        // The known BC 1.86 -> 1.87 boundary changed these JCA services from the
        // raw mechanism secret to the default KDF-3/SHA-256 derived AES key.
        // Compare semantics on each side of that boundary, rather than assuming
        // that every future BC release changes the KDF in the same direction.
        boolean sameDefaultKdfSemantics = isAtLeast187(producerVersion) == isAtLeast187(consumerVersion);
        byte[] recovered = tryDecapsulateVector(vectors, prefix, algorithm);
        byte[] expected = decode(vectors, prefix + ".secret");
        if (sameDefaultKdfSemantics) {
            assertArrayEquals(expected, recovered,
                    algorithm + " must preserve its derived AES key on the same side of the BC 1.87 KDF boundary");
        } else {
            assertFalse(MessageDigest.isEqual(expected, recovered),
                    algorithm + " must retain the known raw-secret / default-KDF distinction across BC 1.86/1.87");
        }
    }

    private static void verifyPublicKeyVector(
            Properties vectors, String prefix, PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        String producerVersion = vectors.getProperty(prefix + ".providerVersion");
        String providerName = algorithm.getBcProvider("KeyGenerator");
        Provider consumerProvider = Security.getProvider(providerName);
        assertNotNull(consumerProvider, "KEM provider must be registered: " + providerName);
        String consumerVersion = consumerProvider.getVersionStr();

        // The BC 1.87 HQC specification change is the known public-key format boundary. The other vectors are
        // expected to remain importable across the currently exercised 1.86/1.87 pair.
        boolean expected = algorithm != PQCKeyEncapsulationAlgorithms.HQC
                || isAtLeast187(producerVersion) == isAtLeast187(consumerVersion);
        String explicitHqcExpectation = System.getProperty("camel.pqc.interop.expectHqcCompatible");
        if (algorithm == PQCKeyEncapsulationAlgorithms.HQC
                && explicitHqcExpectation != null && !explicitHqcExpectation.isBlank()) {
            expected = Boolean.parseBoolean(explicitHqcExpectation);
        }
        boolean compatible = tryVerifyPublicKeyVector(vectors, prefix, algorithm);
        assertTrue(compatible == expected,
                algorithm + " public-key interop changed across BC versions. producer=" + producerVersion
                                           + ", consumer=" + consumerVersion);
    }

    private static boolean tryVerifyPublicKeyVector(
            Properties vectors, String prefix, PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        try {
            String providerName = algorithm.getBcProvider("KeyFactory");
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getAlgorithm(), providerName);
            PublicKey publicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(decode(vectors, prefix + ".publicKey")));
            PrivateKey privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(decode(vectors, prefix + ".privateKey")));
            Encapsulation encapsulation = encapsulate(algorithm, publicKey);
            byte[] recovered = decapsulate(algorithm, privateKey, encapsulation.ciphertext());
            return MessageDigest.isEqual(encapsulation.secret(), recovered);
        } catch (GeneralSecurityException | ProviderException | IllegalArgumentException
                 | ClassCastException expectedIncompatibility) {
            return false;
        }
    }

    private static boolean tryVerifyVector(
            Properties vectors, String prefix,
            PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        try {
            byte[] recovered = tryDecapsulateVector(vectors, prefix, algorithm);
            return MessageDigest.isEqual(decode(vectors, prefix + ".secret"), recovered);
        } catch (GeneralSecurityException | ProviderException | IllegalArgumentException expectedIncompatibility) {
            return false;
        }
    }

    private static byte[] tryDecapsulateVector(
            Properties vectors, String prefix,
            PQCKeyEncapsulationAlgorithms algorithm)
            throws Exception {
        String providerName = algorithm.getBcProvider("KeyFactory");
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getAlgorithm(), providerName);
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decode(vectors, prefix + ".privateKey")));
        return decapsulate(algorithm, privateKey, decode(vectors, prefix + ".ciphertext"));
    }

    private static byte[] decode(Properties vectors, String property) {
        String value = vectors.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing KEM interop vector property: " + property);
        }
        return Base64.getDecoder().decode(value);
    }

    private static boolean isAtLeast187(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            String minorDigits = parts[1].replaceFirst("[^0-9].*$", "");
            int minor = Integer.parseInt(minorDigits);
            return major > 1 || (major == 1 && minor >= 87);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record Encapsulation(byte[] ciphertext, byte[] secret) {
    }
}
