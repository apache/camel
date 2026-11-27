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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

/**
 * The single compatibility boundary for PQC KEM operations.
 *
 * <p>
 * On Java 21+, overloads that receive an algorithm and provider use the standard {@link KEM} API when that provider
 * advertises a {@code KEM} service. Bouncy Castle 1.86 and 1.87 expose standard KEM services for ML-KEM, HQC, CMCE,
 * FrodoKEM, NTRU, NTRU LPRime and SNTRU Prime. Algorithms without a JDK KEM service (notably BIKE and SABER) use the
 * provider's established {@link KeyGenerator} / KEM parameter-spec API. When a caller supplies a {@link KeyGenerator}
 * instance, this class always uses that exact instance; it never silently substitutes the JDK KEM service. This
 * preserves configured Camel generators and their locking/instrumentation behavior.
 * </p>
 *
 * <p>
 * The BC-specific KTS/KEM parameter specs and the optional legacy carrier are loaded from the selected provider's own
 * class loader. No BC implementation classes are statically linked by this component. The resulting body remains a
 * standard {@link SecretKey}; where BC's carrier is visible it is retained as the runtime object for source/binary
 * compatibility with Camel routes that have historically inspected {@code getEncapsulation()}.
 * </p>
 */
public final class BouncyCastleKemSupport {

    private static final String KEM_SERVICE = "KEM";
    private static final String KEY_GENERATOR_SERVICE = "KeyGenerator";
    private static final String BC_KTS_PARAMETER_SPEC = "org.bouncycastle.jcajce.spec.KTSParameterSpec";
    private static final String BC_KEM_GENERATE_SPEC = "org.bouncycastle.jcajce.spec.KEMGenerateSpec";
    private static final String BC_KEM_EXTRACT_SPEC = "org.bouncycastle.jcajce.spec.KEMExtractSpec";
    private static final String BC_SECRET_KEY_WITH_ENCAPSULATION
            = "org.bouncycastle.jcajce.SecretKeyWithEncapsulation";

    private BouncyCastleKemSupport() {
    }

    /** Performs encapsulation with the explicitly supplied KeyGenerator instance. */
    public static EncapsulationResult encapsulate(
            KeyGenerator generator,
            PublicKey publicKey,
            String symmetricAlgorithm,
            int keyLength,
            SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);
        // A caller-supplied KeyGenerator is an explicit Camel extension point. Always execute
        // init()/generateKey() on that exact instance, even when its provider also advertises the
        // standard JDK KEM service. Switching APIs here would bypass configured generators and
        // break applications that wrap or instrument them. The JDK KEM API is selected only by
        // the overload that receives an algorithm and Provider rather than a KeyGenerator.
        synchronized (PQCKemLocks.lockFor(generator, publicKey)) {
            return encapsulateWithKeyGenerator(generator, publicKey, symmetricAlgorithm, keyLength, random);
        }
    }

    /**
     * Performs encapsulation with an explicitly selected provider. This overload also supports providers which expose
     * only the standard JDK KEM service and do not implement a KeyGenerator service for the same algorithm.
     */
    public static EncapsulationResult encapsulate(
            String algorithm,
            Provider provider,
            PublicKey publicKey,
            String symmetricAlgorithm,
            int keyLength,
            SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);

        if (hasService(provider, KEM_SERVICE, algorithm)) {
            return encapsulateWithJdkKem(algorithm, provider, publicKey, symmetricAlgorithm, keyLength, random);
        }

        try {
            KeyGenerator generator = KeyGenerator.getInstance(algorithm, provider);
            synchronized (PQCKemLocks.lockFor(generator, publicKey)) {
                return encapsulateWithKeyGenerator(generator, publicKey, symmetricAlgorithm, keyLength, random);
            }
        } catch (GeneralSecurityException e) {
            throw invalidParameter("Provider '" + provider.getName()
                                   + "' exposes neither a usable KEM nor KeyGenerator service for '" + algorithm + "'",
                    e);
        }
    }

    /** Performs decapsulation with the explicitly supplied KeyGenerator instance. */
    public static SecretKey decapsulate(
            KeyGenerator generator,
            PrivateKey privateKey,
            byte[] encapsulation,
            String symmetricAlgorithm,
            int keyLength,
            SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(encapsulation, "encapsulation");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);
        // The explicitly configured generator must own the complete init()/generateKey()
        // sequence. The provider-based overload remains responsible for selecting the standard
        // JDK KEM API when no KeyGenerator was injected.
        synchronized (PQCKemLocks.lockFor(generator, privateKey)) {
            return decapsulateWithKeyGenerator(
                    generator, privateKey, encapsulation, symmetricAlgorithm, keyLength, random);
        }
    }

    /** Performs decapsulation with an explicitly selected provider. */
    public static SecretKey decapsulate(
            String algorithm,
            Provider provider,
            PrivateKey privateKey,
            byte[] encapsulation,
            String symmetricAlgorithm,
            int keyLength,
            SecureRandom random)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(encapsulation, "encapsulation");
        Objects.requireNonNull(symmetricAlgorithm, "symmetricAlgorithm");
        validateKeyLength(keyLength);

        if (hasService(provider, KEM_SERVICE, algorithm)) {
            return decapsulateWithJdkKem(algorithm, provider, privateKey, encapsulation,
                    symmetricAlgorithm, keyLength, random);
        }

        try {
            KeyGenerator generator = KeyGenerator.getInstance(algorithm, provider);
            synchronized (PQCKemLocks.lockFor(generator, privateKey)) {
                return decapsulateWithKeyGenerator(generator, privateKey, encapsulation,
                        symmetricAlgorithm, keyLength, random);
            }
        } catch (GeneralSecurityException e) {
            throw invalidParameter("Provider '" + provider.getName()
                                   + "' exposes neither a usable KEM nor KeyGenerator service for '" + algorithm + "'",
                    e);
        }
    }

    /** Returns a defensive copy of the encapsulation carried by a generated SecretKey body. */
    public static byte[] getEncapsulation(SecretKey secretKey) throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(secretKey, "secretKey");
        if (secretKey instanceof EncapsulatedSecretKey encapsulated) {
            return encapsulated.getEncapsulation();
        }
        try {
            Method accessor = secretKey.getClass().getMethod("getEncapsulation");
            Object value = accessor.invoke(secretKey);
            if (!(value instanceof byte[] bytes) || bytes.length == 0) {
                throw new InvalidAlgorithmParameterException(
                        "KEM SecretKey carrier '" + secretKey.getClass().getName()
                                                             + "' returned an empty encapsulation");
            }
            return bytes.clone();
        } catch (NoSuchMethodException e) {
            throw invalidParameter("SecretKey '" + secretKey.getClass().getName()
                                   + "' does not carry a KEM encapsulation. Pass the SecretKey body returned by "
                                   + "generateSecretKeyEncapsulation.",
                    e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw invalidParameter("Could not read the KEM encapsulation from SecretKey '"
                                   + secretKey.getClass().getName() + "'",
                    unwrap(e));
        }
    }

    /** Returns a defensive copy of the encoded secret key. */
    public static byte[] getEncodedKey(SecretKey secretKey) throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(secretKey, "secretKey");
        byte[] encoded = secretKey.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new InvalidAlgorithmParameterException(
                    "KEM SecretKey '" + secretKey.getClass().getName() + "' does not expose encoded key material");
        }
        return encoded.clone();
    }

    private static EncapsulationResult encapsulateWithJdkKem(
            String algorithm, Provider provider, PublicKey publicKey, String symmetricAlgorithm,
            int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            KEM kem = KEM.getInstance(algorithm, provider);
            AlgorithmParameterSpec spec = newKtsParameterSpec(provider, symmetricAlgorithm, keyLength);
            KEM.Encapsulator encapsulator = kem.newEncapsulator(publicKey, spec, random);
            KEM.Encapsulated encapsulated;
            if (spec != null) {
                // BC's KTSParameterSpec default is KDF-3/SHA-256, matching the legacy KEMGenerateSpec default.
                encapsulated = encapsulator.encapsulate();
            } else {
                int bytes = keyLength / Byte.SIZE;
                if (bytes > encapsulator.secretSize()) {
                    throw new InvalidAlgorithmParameterException(
                            "Provider '" + provider.getName() + "' has no KTSParameterSpec and its raw '" + algorithm
                                                                 + "' shared secret is only "
                                                                 + encapsulator.secretSize() * Byte.SIZE
                                                                 + " bits; it cannot produce the requested " + keyLength
                                                                 + "-bit key without a KDF");
                }
                encapsulated = encapsulator.encapsulate(0, bytes, symmetricAlgorithm);
            }
            byte[] message = encapsulated.encapsulation();
            if (message == null || message.length == 0) {
                throw new InvalidAlgorithmParameterException(
                        "Provider '" + provider.getName() + "' returned an empty KEM encapsulation for '"
                                                             + algorithm + "'");
            }
            SecretKey carried = carryEncapsulation(encapsulated.key(), message, provider);
            return new EncapsulationResult(carried, message);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalidParameter("JDK KEM encapsulation failed for '" + algorithm + "' using provider '"
                                   + provider.getName() + "'",
                    e);
        }
    }

    private static SecretKey decapsulateWithJdkKem(
            String algorithm, Provider provider, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
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
                    throw new InvalidAlgorithmParameterException(
                            "Provider '" + provider.getName() + "' has no KTSParameterSpec and its raw '" + algorithm
                                                                 + "' shared secret is only "
                                                                 + decapsulator.secretSize() * Byte.SIZE
                                                                 + " bits; it cannot recover the requested " + keyLength
                                                                 + "-bit key without a KDF");
                }
                extracted = decapsulator.decapsulate(encapsulation, 0, bytes, symmetricAlgorithm);
            }
            return carryEncapsulation(extracted, encapsulation, provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalidParameter("JDK KEM decapsulation failed for '" + algorithm + "' using provider '"
                                   + provider.getName() + "'",
                    e);
        }
    }

    private static EncapsulationResult encapsulateWithKeyGenerator(
            KeyGenerator generator, PublicKey publicKey, String symmetricAlgorithm,
            int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            AlgorithmParameterSpec spec = newBcParameterSpec(generator.getProvider(), BC_KEM_GENERATE_SPEC,
                    new Class<?>[] { PublicKey.class, String.class, int.class },
                    new Object[] { publicKey, symmetricAlgorithm, keyLength });
            generator.init(spec, random);
            SecretKey generated = generator.generateKey();
            byte[] encapsulation = getEncapsulation(generated);
            return new EncapsulationResult(generated, encapsulation);
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidParameter("Legacy KeyGenerator encapsulation failed for '" + generator.getAlgorithm()
                                   + "' using provider '" + generator.getProvider().getName() + "'",
                    e);
        }
    }

    private static SecretKey decapsulateWithKeyGenerator(
            KeyGenerator generator, PrivateKey privateKey, byte[] encapsulation,
            String symmetricAlgorithm, int keyLength, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        try {
            AlgorithmParameterSpec spec = newBcParameterSpec(generator.getProvider(), BC_KEM_EXTRACT_SPEC,
                    new Class<?>[] { PrivateKey.class, byte[].class, String.class, int.class },
                    new Object[] { privateKey, encapsulation.clone(), symmetricAlgorithm, keyLength });
            generator.init(spec, random);
            SecretKey generated = generator.generateKey();
            // Validate that legacy providers continue returning a carrier usable by Camel's extract operations.
            getEncapsulation(generated);
            return generated;
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidParameter("Legacy KeyGenerator decapsulation failed for '" + generator.getAlgorithm()
                                   + "' using provider '" + generator.getProvider().getName() + "'",
                    e);
        }
    }

    private static AlgorithmParameterSpec newKtsParameterSpec(
            Provider provider, String symmetricAlgorithm, int keyLength)
            throws InvalidAlgorithmParameterException {
        try {
            Class<?> builderType;
            try {
                builderType = loadProviderClass(provider, BC_KTS_PARAMETER_SPEC + "$Builder");
            } catch (ClassNotFoundException e) {
                // Non-BC standard KEM providers need not expose BC's optional KTSParameterSpec.
                return null;
            }
            Object builder = builderType.getConstructor(String.class, int.class)
                    .newInstance(symmetricAlgorithm, keyLength);
            Object spec = builderType.getMethod("build").invoke(builder);
            if (!(spec instanceof AlgorithmParameterSpec parameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                        "Provider '" + provider.getName()
                                                             + "' KTSParameterSpec.Builder.build() did not return an AlgorithmParameterSpec");
            }
            return parameterSpec;
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw invalidParameter("Could not create the provider-specific KTSParameterSpec from provider '"
                                   + provider.getName() + "'",
                    unwrap(e));
        }
    }

    private static AlgorithmParameterSpec newBcParameterSpec(
            Provider provider, String className, Class<?>[] parameterTypes, Object[] arguments)
            throws InvalidAlgorithmParameterException {
        try {
            Class<?> specType = loadProviderClass(provider, className);
            Constructor<?> constructor = specType.getConstructor(parameterTypes);
            Object result = constructor.newInstance(arguments);
            if (!(result instanceof AlgorithmParameterSpec parameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                        "Provider-specific KEM spec '" + className + "' does not implement AlgorithmParameterSpec");
            }
            return parameterSpec;
        } catch (InvalidAlgorithmParameterException e) {
            throw e;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw invalidParameter("Provider '" + provider.getName()
                                   + "' cannot construct compatible KEM parameter spec " + className
                                   + ". Use a BC-compatible KeyGenerator provider or a standard JDK KEM service.",
                    unwrap(e));
        }
    }

    private static Class<?> loadProviderClass(Provider provider, String className) throws ClassNotFoundException {
        ClassLoader providerLoader = provider == null ? null : provider.getClass().getClassLoader();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader componentLoader = BouncyCastleKemSupport.class.getClassLoader();
        ClassLoader[] loaders = { providerLoader, contextLoader, componentLoader };
        ClassNotFoundException failure = null;
        for (int i = 0; i < loaders.length; i++) {
            ClassLoader loader = loaders[i];
            if (loader == null || alreadyTried(loaders, i, loader)) {
                continue;
            }
            try {
                return Class.forName(className, true, loader);
            } catch (ClassNotFoundException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return Class.forName(className);
    }

    private static boolean alreadyTried(ClassLoader[] loaders, int before, ClassLoader candidate) {
        for (int i = 0; i < before; i++) {
            if (loaders[i] == candidate) {
                return true;
            }
        }
        return false;
    }

    private static SecretKey carryEncapsulation(SecretKey secretKey, byte[] encapsulation, Provider provider)
            throws InvalidAlgorithmParameterException {
        if (secretKey == null) {
            throw new InvalidAlgorithmParameterException("Provider returned a null KEM SecretKey");
        }
        if (encapsulation == null || encapsulation.length == 0) {
            throw new InvalidAlgorithmParameterException("Provider returned an empty KEM encapsulation");
        }
        try {
            Class<?> carrierType = loadProviderClass(provider, BC_SECRET_KEY_WITH_ENCAPSULATION);
            if (SecretKey.class.isAssignableFrom(carrierType)) {
                Constructor<?> constructor = carrierType.getConstructor(SecretKey.class, byte[].class);
                return (SecretKey) constructor.newInstance(secretKey, encapsulation.clone());
            }
        } catch (ClassNotFoundException e) {
            // A non-BC provider may return a standard SecretKey without a BC carrier implementation.
        } catch (ReflectiveOperationException | LinkageError e) {
            throw invalidParameter("Could not wrap the JDK KEM result in the provider's "
                                   + "SecretKeyWithEncapsulation carrier",
                    unwrap(e));
        }
        return new EncapsulatedSecretKey(secretKey, encapsulation);
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

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return throwable;
    }

    /** Standard SecretKey facade used only when a JDK KEM provider has no BC carrier class. */
    private static final class EncapsulatedSecretKey implements SecretKey {
        private static final long serialVersionUID = 1L;
        private final SecretKey delegate;
        private final byte[] encapsulation;

        private EncapsulatedSecretKey(SecretKey delegate, byte[] encapsulation) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.encapsulation = encapsulation.clone();
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
