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

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

import javax.crypto.KEM;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jcajce.spec.KTSParameterSpec;

/** Typed BC/JCA boundary for encapsulation and decapsulation. */
public final class BouncyCastleKemSupport {

    private static final String KEM_SERVICE = "KEM";
    private static final String KEY_GENERATOR_SERVICE = "KeyGenerator";

    private BouncyCastleKemSupport() {
    }

    /** Always uses the exact KeyGenerator supplied by the Camel endpoint/registry. */
    public static EncapsulationResult encapsulate(
            KeyGenerator generator, PublicKey publicKey, String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);
        synchronized (PQCKemLocks.lockFor(generator, publicKey)) {
            return encapsulateWithKeyGenerator(generator, publicKey, symmetricAlgorithm, keyLength, random);
        }
    }

    /** Prefer BC KeyGenerator on both releases; on BC 1.86 this is required for legacy KEM interoperability. */
    public static EncapsulationResult encapsulate(
            String algorithm, Provider provider, PublicKey publicKey, String symmetricAlgorithm,
            int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);

        if (hasService(provider, KEY_GENERATOR_SERVICE, algorithm)) {
            KeyGenerator generator = createKeyGenerator(algorithm, provider);
            synchronized (PQCKemLocks.lockFor(generator, publicKey)) {
                return encapsulateWithKeyGenerator(generator, publicKey, symmetricAlgorithm, keyLength, random);
            }
        }
        if (hasService(provider, KEM_SERVICE, algorithm)) {
            return encapsulateWithJdkKem(algorithm, provider, publicKey, symmetricAlgorithm, keyLength, random);
        }
        throw invalidParameter("Provider '" + provider.getName()
                               + "' exposes neither KeyGenerator nor KEM for '" + algorithm + "'",
                new GeneralSecurityException("No matching KEM service"));
    }

    /** Always uses the exact KeyGenerator supplied by the Camel endpoint/registry. */
    public static SecretKey decapsulate(
            KeyGenerator generator, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(encapsulation, "encapsulation");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);
        synchronized (PQCKemLocks.lockFor(generator, privateKey)) {
            return decapsulateWithKeyGenerator(generator, privateKey, encapsulation, symmetricAlgorithm, keyLength, random);
        }
    }

    /** Prefer BC KeyGenerator on both releases; use JCA KEM only if no KeyGenerator service exists. */
    public static SecretKey decapsulate(
            String algorithm, Provider provider, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(encapsulation, "encapsulation");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);

        if (hasService(provider, KEY_GENERATOR_SERVICE, algorithm)) {
            KeyGenerator generator = createKeyGenerator(algorithm, provider);
            synchronized (PQCKemLocks.lockFor(generator, privateKey)) {
                return decapsulateWithKeyGenerator(
                        generator, privateKey, encapsulation, symmetricAlgorithm, keyLength, random);
            }
        }
        if (hasService(provider, KEM_SERVICE, algorithm)) {
            return decapsulateWithJdkKem(algorithm, provider, privateKey, encapsulation,
                    symmetricAlgorithm, keyLength);
        }
        throw invalidParameter("Provider '" + provider.getName()
                               + "' exposes neither KeyGenerator nor KEM for '" + algorithm + "'",
                new GeneralSecurityException("No matching KEM service"));
    }

    /** Return a copy of the ciphertext carried by either BC's typed carrier or Camel's JDK-KEM wrapper. */
    public static byte[] getEncapsulation(SecretKey secretKey) throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(secretKey, "secretKey");
        final byte[] encapsulation;
        if (secretKey instanceof SecretKeyWithEncapsulation bcCarrier) {
            encapsulation = bcCarrier.getEncapsulation();
        } else if (secretKey instanceof EncapsulatedSecretKey camelCarrier) {
            encapsulation = camelCarrier.getEncapsulation();
        } else {
            throw invalidParameter("SecretKey '" + secretKey.getClass().getName()
                                   + "' does not carry a KEM encapsulation. Use the key returned by "
                                   + "generateSecretKeyEncapsulation.",
                    null);
        }
        if (encapsulation == null || encapsulation.length == 0) {
            throw invalidParameter("KEM SecretKey carrier returned an empty encapsulation", null);
        }
        return encapsulation.clone();
    }

    /** Returns a defensive copy of the encoded shared secret. */
    public static byte[] getEncodedKey(SecretKey secretKey) throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(secretKey, "secretKey");
        byte[] encoded = secretKey.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw invalidParameter("KEM SecretKey '" + secretKey.getClass().getName()
                                   + "' does not expose encoded key material",
                    null);
        }
        return encoded.clone();
    }

    private static KeyGenerator createKeyGenerator(String algorithm, Provider provider)
            throws InvalidAlgorithmParameterException {
        try {
            return KeyGenerator.getInstance(algorithm, provider);
        } catch (GeneralSecurityException e) {
            throw invalidParameter("Provider '" + provider.getName()
                                   + "' advertises but cannot create KeyGenerator for '" + algorithm + "'",
                    e);
        }
    }

    private static EncapsulationResult encapsulateWithKeyGenerator(
            KeyGenerator generator, PublicKey publicKey, String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            KEMGenerateSpec spec = new KEMGenerateSpec.Builder(publicKey, symmetricAlgorithm, keyLength).build();
            generator.init(spec, random);
            SecretKey generated = generator.generateKey();
            byte[] encapsulation = getEncapsulation(generated);
            return new EncapsulationResult(generated, encapsulation);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidParameter("KeyGenerator encapsulation failed for '" + generator.getAlgorithm()
                                   + "' using provider '" + generator.getProvider().getName() + "'",
                    e);
        }
    }

    private static SecretKey decapsulateWithKeyGenerator(
            KeyGenerator generator, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            KEMExtractSpec spec = new KEMExtractSpec.Builder(
                    privateKey, encapsulation.clone(), symmetricAlgorithm, keyLength).build();
            generator.init(spec, random);
            SecretKey generated = generator.generateKey();
            getEncapsulation(generated);
            return generated;
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidParameter("KeyGenerator decapsulation failed for '" + generator.getAlgorithm()
                                   + "' using provider '" + generator.getProvider().getName() + "'",
                    e);
        }
    }

    private static EncapsulationResult encapsulateWithJdkKem(
            String algorithm, Provider provider, PublicKey publicKey,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            KEM kem = KEM.getInstance(algorithm, provider);
            AlgorithmParameterSpec spec = newKtsParameterSpec(provider, symmetricAlgorithm, keyLength);
            KEM.Encapsulator encapsulator = kem.newEncapsulator(publicKey, spec, random);
            KEM.Encapsulated encapsulated;
            if (spec != null) {
                encapsulated = encapsulator.encapsulate();
            } else {
                int bytes = keyLength / Byte.SIZE;
                if (bytes > encapsulator.secretSize()) {
                    throw invalidParameter("Provider '" + provider.getName() + "' has only "
                                           + encapsulator.secretSize() * Byte.SIZE + " raw bits for '" + algorithm
                                           + "'; cannot derive the requested " + keyLength + "-bit key",
                            null);
                }
                encapsulated = encapsulator.encapsulate(0, bytes, symmetricAlgorithm);
            }
            byte[] message = encapsulated.encapsulation();
            if (message == null || message.length == 0) {
                throw invalidParameter("Provider '" + provider.getName()
                                       + "' returned an empty KEM encapsulation for '" + algorithm + "'",
                        null);
            }
            SecretKey carried = new EncapsulatedSecretKey(encapsulated.key(), message);
            return new EncapsulationResult(carried, message);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalidParameter("JDK KEM encapsulation failed for '" + algorithm
                                   + "' using provider '" + provider.getName() + "'",
                    e);
        }
    }

    private static SecretKey decapsulateWithJdkKem(
            String algorithm, Provider provider, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength)
            throws InvalidAlgorithmParameterException {
        try {
            KEM kem = KEM.getInstance(algorithm, provider);
            AlgorithmParameterSpec spec = newKtsParameterSpec(provider, symmetricAlgorithm, keyLength);
            KEM.Decapsulator decapsulator = kem.newDecapsulator(privateKey, spec);
            SecretKey extracted;
            if (spec != null) {
                extracted = decapsulator.decapsulate(encapsulation);
            } else {
                int bytes = keyLength / Byte.SIZE;
                if (bytes > decapsulator.secretSize()) {
                    throw invalidParameter("Provider '" + provider.getName() + "' has only "
                                           + decapsulator.secretSize() * Byte.SIZE + " raw bits for '" + algorithm
                                           + "'; cannot recover the requested " + keyLength + "-bit key",
                            null);
                }
                extracted = decapsulator.decapsulate(encapsulation, 0, bytes, symmetricAlgorithm);
            }
            return new EncapsulatedSecretKey(extracted, encapsulation);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalidParameter("JDK KEM decapsulation failed for '" + algorithm
                                   + "' using provider '" + provider.getName() + "'",
                    e);
        }
    }

    private static AlgorithmParameterSpec newKtsParameterSpec(
            Provider provider, String symmetricAlgorithm, int keyLength) {
        // Only BC understands its KTSParameterSpec. Other JCA KEM providers use the standard KEM API directly.
        if (!"BC".equals(provider.getName()) && !"BCPQC".equals(provider.getName())) {
            return null;
        }
        return new KTSParameterSpec.Builder(symmetricAlgorithm, keyLength).build();
    }

    private static boolean hasService(Provider provider, String type, String algorithm) {
        return provider != null && provider.getService(type, algorithm) != null;
    }

    private static void validateKeyLength(int keyLength) throws InvalidAlgorithmParameterException {
        if (keyLength <= 0 || keyLength % Byte.SIZE != 0) {
            throw new InvalidAlgorithmParameterException(
                    "KEM symmetric key length must be a positive whole number of bytes: " + keyLength);
        }
    }

    private static InvalidAlgorithmParameterException invalidParameter(String message, Throwable cause) {
        return new InvalidAlgorithmParameterException(message, cause);
    }

    /** Camel-owned carrier keeps the ciphertext explicit without depending on a provider implementation class. */
    public static final class EncapsulatedSecretKey implements SecretKey {
        private static final long serialVersionUID = 1L;
        private final SecretKey delegate;
        private final byte[] encapsulation;

        private EncapsulatedSecretKey(SecretKey delegate, byte[] encapsulation) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.encapsulation = Objects.requireNonNull(encapsulation, "encapsulation").clone();
        }

        public byte[] getEncapsulation() {
            return encapsulation.clone();
        }

        @Override
        public String getAlgorithm() {
            return delegate.getAlgorithm();
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public byte[] getEncoded() {
            byte[] encoded = delegate.getEncoded();
            return encoded == null ? null : encoded.clone();
        }

        @Override
        public boolean equals(Object other) {
            return delegate.equals(other instanceof EncapsulatedSecretKey key ? key.delegate : other);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    /** The standard SecretKey view and its associated KEM ciphertext. */
    public record EncapsulationResult(SecretKey secretKey, byte[] encapsulation) {
        public EncapsulationResult {
            Objects.requireNonNull(secretKey, "secretKey");
            Objects.requireNonNull(encapsulation, "encapsulation");
            encapsulation = encapsulation.clone();
        }

        @Override
        public byte[] encapsulation() {
            return encapsulation.clone();
        }
    }
}
