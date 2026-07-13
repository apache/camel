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
import java.security.Security;
import java.security.Signature;
import java.security.spec.AlgorithmParameterSpec;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pqc.crypto.*;
import org.apache.camel.component.pqc.crypto.hybrid.*;
import org.apache.camel.component.pqc.crypto.kem.*;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyRotationScheduler;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.SecureRandomHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

/**
 * For working with Post Quantum Cryptography Algorithms
 */
@Component("pqc")
public class PQCComponent extends HealthCheckComponent {

    @Metadata
    private PQCConfiguration configuration = new PQCConfiguration();
    @Metadata(label = "advanced", defaultValue = "false",
              description = "Whether to start an automated background key rotation scheduler for this component."
                            + " Requires keyLifecycleManager to be set. The scheduler periodically rotates keys that"
                            + " exceed the configured age and/or usage policy.")
    private boolean keyRotationSchedulerEnabled;
    @Metadata(label = "advanced", defaultValue = "3600000", javaType = "java.time.Duration",
              description = "Interval between key rotation checks when the scheduler is enabled.")
    private long keyRotationCheckInterval = 3600000L;
    @Metadata(label = "advanced", javaType = "java.time.Duration",
              description = "When the scheduler is enabled, rotate keys older than this age. If not set, age is not used"
                            + " as a rotation signal.")
    private long keyRotationMaxAge;
    @Metadata(label = "advanced", defaultValue = "0",
              description = "When the scheduler is enabled, rotate keys whose recorded usage count reaches this value. 0"
                            + " disables usage-based rotation.")
    private long keyRotationMaxUsage;

    private KeyRotationScheduler keyRotationScheduler;

    // Key pairs generated for a specific parameterSpec, cached per algorithm and parameter set so that endpoints
    // sharing the same configuration (for example a sign and a verify endpoint) use the same key
    private final Map<String, KeyPair> parameterSpecKeyPairs = new ConcurrentHashMap<>();

    public PQCComponent() {
        this(null);
    }

    public PQCComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        PQCConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new PQCConfiguration();
        PQCEndpoint endpoint = new PQCEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        // When a parameterSpec (NIST security level) is configured, generate the key material with that parameter set
        // instead of using the algorithm's hardcoded default material
        if (ObjectHelper.isNotEmpty(configuration.getParameterSpec())) {
            configureParameterSpecMaterial(configuration);
        }

        if (ObjectHelper.isEmpty(configuration.getSigner()) && ObjectHelper.isEmpty(configuration.getKeyPair())
                && ObjectHelper.isEmpty(configuration.getKeyStore()) && ObjectHelper.isEmpty(configuration.getKeyPairAlias())) {
            if (ObjectHelper.isNotEmpty(configuration.getSignatureAlgorithm())) {
                switch (configuration.getSignatureAlgorithm()) {
                    case "MLDSA":
                        configuration.setSigner(PQCDefaultMLDSAMaterial.signer);
                        configuration.setKeyPair(PQCDefaultMLDSAMaterial.keyPair);
                        break;
                    case "SLHDSA":
                        configuration.setSigner(PQCDefaultSLHDSAMaterial.signer);
                        configuration.setKeyPair(PQCDefaultSLHDSAMaterial.keyPair);
                        break;
                    case "LMS":
                        configuration.setSigner(PQCDefaultLMSMaterial.signer);
                        configuration.setKeyPair(PQCDefaultLMSMaterial.keyPair);
                        break;
                    case "XMSS":
                        configuration.setSigner(PQCDefaultXMSSMaterial.signer);
                        configuration.setKeyPair(PQCDefaultXMSSMaterial.keyPair);
                        break;
                    case "FALCON":
                        configuration.setSigner(PQCDefaultFalconMaterial.signer);
                        configuration.setKeyPair(PQCDefaultFalconMaterial.keyPair);
                        break;
                    case "PICNIC":
                        configuration.setSigner(PQCDefaultPicnicMaterial.signer);
                        configuration.setKeyPair(PQCDefaultPicnicMaterial.keyPair);
                        break;
                    case "SNOVA":
                        configuration.setSigner(PQCDefaultSNOVAMaterial.signer);
                        configuration.setKeyPair(PQCDefaultSNOVAMaterial.keyPair);
                        break;
                    case "MAYO":
                        configuration.setSigner(PQCDefaultMAYOMaterial.signer);
                        configuration.setKeyPair(PQCDefaultMAYOMaterial.keyPair);
                        break;
                    case "DILITHIUM":
                        configuration.setSigner(PQCDefaultDILITHIUMMaterial.signer);
                        configuration.setKeyPair(PQCDefaultDILITHIUMMaterial.keyPair);
                        break;
                    case "SPHINCSPLUS":
                        configuration.setSigner(PQCDefaultSPHINCSPLUSMaterial.signer);
                        configuration.setKeyPair(PQCDefaultSPHINCSPLUSMaterial.keyPair);
                        break;
                    default:
                        break;
                }
            }
        }

        if (ObjectHelper.isEmpty(configuration.getKeyGenerator()) && ObjectHelper.isEmpty(configuration.getKeyPair())
                && ObjectHelper.isEmpty(configuration.getKeyStore()) && ObjectHelper.isEmpty(configuration.getKeyPairAlias())) {
            if (ObjectHelper.isNotEmpty(configuration.getKeyEncapsulationAlgorithm())) {
                switch (configuration.getKeyEncapsulationAlgorithm()) {
                    case "MLKEM":
                        configuration.setKeyGenerator(PQCDefaultMLKEMMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultMLKEMMaterial.keyPair);
                        break;
                    case "BIKE":
                        configuration.setKeyGenerator(PQCDefaultBIKEMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultBIKEMaterial.keyPair);
                        break;
                    case "HQC":
                        configuration.setKeyGenerator(PQCDefaultHQCMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultHQCMaterial.keyPair);
                        break;
                    case "CMCE":
                        configuration.setKeyGenerator(PQCDefaultCMCEMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultCMCEMaterial.keyPair);
                        break;
                    case "SABER":
                        configuration.setKeyGenerator(PQCDefaultSABERMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultSABERMaterial.keyPair);
                        break;
                    case "FRODO":
                        configuration.setKeyGenerator(PQCDefaultFRODOMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultFRODOMaterial.keyPair);
                        break;
                    case "NTRU":
                        configuration.setKeyGenerator(PQCDefaultNTRUMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultNTRUMaterial.keyPair);
                        break;
                    case "NTRULPRime":
                        configuration.setKeyGenerator(PQCDefaultNTRULPRimeMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultNTRULPRimeMaterial.keyPair);
                        break;
                    case "KYBER":
                        configuration.setKeyGenerator(PQCDefaultKYBERMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultKYBERMaterial.keyPair);
                        break;
                    default:
                        break;
                }
            }
        }

        // Auto-configure hybrid signature materials
        configureHybridSignatureMaterial(configuration);

        // Auto-configure hybrid KEM materials
        configureHybridKEMMaterial(configuration);

        return endpoint;
    }

    /**
     * Generates the key material for the configured parameterSpec (the NIST parameter set / security level) instead of
     * using the algorithm's hardcoded default material. Key material supplied explicitly by the route author always
     * wins and is never regenerated.
     */
    private void configureParameterSpecMaterial(PQCConfiguration configuration) throws Exception {
        PQCOperations operation = configuration.getOperation();
        if (operation != null && isHybridOperation(operation)) {
            // The hybrid operations pair a specific classical key set with a matching PQC key set, so a custom
            // parameter set cannot be applied without breaking that pairing
            throw new IllegalArgumentException(
                    "The parameterSpec option is not supported for hybrid operations. Configure the hybrid key material"
                                               + " explicitly (keyPair and classicalKeyPair) instead.");
        }

        if (ObjectHelper.isNotEmpty(configuration.getKeyPair()) || ObjectHelper.isNotEmpty(configuration.getKeyStore())
                || ObjectHelper.isNotEmpty(configuration.getKeyPairAlias())) {
            // The route author supplied the key material explicitly
            return;
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        String spec = configuration.getParameterSpec();
        String signatureAlgorithm = configuration.getSignatureAlgorithm();
        String kemAlgorithm = configuration.getKeyEncapsulationAlgorithm();

        if (ObjectHelper.isNotEmpty(signatureAlgorithm)) {
            PQCSignatureAlgorithms algorithm = PQCSignatureAlgorithms.valueOf(signatureAlgorithm);
            configuration.setKeyPair(
                    resolveKeyPair(signatureAlgorithm, spec, algorithm.getAlgorithm(), algorithm.getBcProvider()));
            if (ObjectHelper.isEmpty(configuration.getSigner())) {
                configuration.setSigner(Signature.getInstance(algorithm.getAlgorithm(), algorithm.getBcProvider()));
            }
        } else if (ObjectHelper.isNotEmpty(kemAlgorithm)) {
            PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
            configuration.setKeyPair(
                    resolveKeyPair(kemAlgorithm, spec, algorithm.getAlgorithm(), algorithm.getBcProvider()));
            if (ObjectHelper.isEmpty(configuration.getKeyGenerator())) {
                configuration.setKeyGenerator(
                        KeyGenerator.getInstance(algorithm.getAlgorithm(), algorithm.getBcProvider()));
            }
        } else {
            throw new IllegalArgumentException(
                    "The parameterSpec option requires either signatureAlgorithm or keyEncapsulationAlgorithm to be"
                                               + " set");
        }
    }

    /**
     * Returns the key pair for the given algorithm and parameter set, generating it on first use. The key pair is
     * cached per algorithm and parameter set so that endpoints sharing the same configuration - for example a sign and
     * a verify endpoint - use the same key, mirroring the behaviour of the shared default key material.
     */
    private KeyPair resolveKeyPair(String algorithm, String parameterSpec, String jceAlgorithm, String provider)
            throws Exception {
        String cacheKey = algorithm + ':' + parameterSpec;
        KeyPair keyPair = parameterSpecKeyPairs.get(cacheKey);
        if (keyPair == null) {
            AlgorithmParameterSpec spec = PQCParameterSpecResolver.resolve(algorithm, parameterSpec);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(jceAlgorithm, provider);
            generator.initialize(spec, SecureRandomHelper.getSecureRandom());
            keyPair = generator.generateKeyPair();
            KeyPair existing = parameterSpecKeyPairs.putIfAbsent(cacheKey, keyPair);
            if (existing != null) {
                keyPair = existing;
            }
        }
        return keyPair;
    }

    private static boolean isHybridOperation(PQCOperations operation) {
        return operation == PQCOperations.hybridSign
                || operation == PQCOperations.hybridVerify
                || operation == PQCOperations.hybridGenerateSecretKeyEncapsulation
                || operation == PQCOperations.hybridExtractSecretKeyEncapsulation
                || operation == PQCOperations.hybridExtractSecretKeyFromEncapsulation;
    }

    /**
     * Auto-configures hybrid signature materials based on classical and PQC algorithm settings.
     */
    private void configureHybridSignatureMaterial(PQCConfiguration configuration) {
        // Only configure if hybrid signature operations and classical algorithm is specified
        if (configuration.getOperation() != PQCOperations.hybridSign
                && configuration.getOperation() != PQCOperations.hybridVerify) {
            return;
        }

        // Configure PQC signature material if not already set
        if (ObjectHelper.isEmpty(configuration.getSigner()) && ObjectHelper.isEmpty(configuration.getKeyPair())) {
            if (ObjectHelper.isNotEmpty(configuration.getSignatureAlgorithm())) {
                switch (configuration.getSignatureAlgorithm()) {
                    case "MLDSA":
                        configuration.setSigner(PQCDefaultMLDSAMaterial.signer);
                        configuration.setKeyPair(PQCDefaultMLDSAMaterial.keyPair);
                        break;
                    case "SLHDSA":
                        configuration.setSigner(PQCDefaultSLHDSAMaterial.signer);
                        configuration.setKeyPair(PQCDefaultSLHDSAMaterial.keyPair);
                        break;
                    case "DILITHIUM":
                        configuration.setSigner(PQCDefaultDILITHIUMMaterial.signer);
                        configuration.setKeyPair(PQCDefaultDILITHIUMMaterial.keyPair);
                        break;
                    default:
                        break;
                }
            }
        }

        // Configure classical signature material if not already set
        if (ObjectHelper.isEmpty(configuration.getClassicalSigner())
                && ObjectHelper.isEmpty(configuration.getClassicalKeyPair())) {
            if (ObjectHelper.isNotEmpty(configuration.getClassicalSignatureAlgorithm())) {
                String classicalAlg = configuration.getClassicalSignatureAlgorithm();
                String pqcAlg = configuration.getSignatureAlgorithm();

                // Use pre-built hybrid materials for common combinations
                if ("ECDSA_P256".equals(classicalAlg) && "MLDSA".equals(pqcAlg)) {
                    configuration.setClassicalSigner(PQCDefaultECDSAMLDSAMaterial.classicalSigner);
                    configuration.setClassicalKeyPair(PQCDefaultECDSAMLDSAMaterial.classicalKeyPair);
                    configuration.setSigner(PQCDefaultECDSAMLDSAMaterial.pqcSigner);
                    configuration.setKeyPair(PQCDefaultECDSAMLDSAMaterial.pqcKeyPair);
                } else if ("ED25519".equals(classicalAlg) && "MLDSA".equals(pqcAlg)) {
                    configuration.setClassicalSigner(PQCDefaultEd25519MLDSAMaterial.classicalSigner);
                    configuration.setClassicalKeyPair(PQCDefaultEd25519MLDSAMaterial.classicalKeyPair);
                    configuration.setSigner(PQCDefaultEd25519MLDSAMaterial.pqcSigner);
                    configuration.setKeyPair(PQCDefaultEd25519MLDSAMaterial.pqcKeyPair);
                }
            }
        }
    }

    /**
     * Auto-configures hybrid KEM materials based on classical and PQC algorithm settings.
     */
    private void configureHybridKEMMaterial(PQCConfiguration configuration) {
        // Only configure if hybrid KEM operations and classical algorithm is specified
        if (configuration.getOperation() != PQCOperations.hybridGenerateSecretKeyEncapsulation
                && configuration.getOperation() != PQCOperations.hybridExtractSecretKeyEncapsulation
                && configuration.getOperation() != PQCOperations.hybridExtractSecretKeyFromEncapsulation) {
            return;
        }

        // Configure PQC KEM material if not already set
        if (ObjectHelper.isEmpty(configuration.getKeyGenerator()) && ObjectHelper.isEmpty(configuration.getKeyPair())) {
            if (ObjectHelper.isNotEmpty(configuration.getKeyEncapsulationAlgorithm())) {
                switch (configuration.getKeyEncapsulationAlgorithm()) {
                    case "MLKEM":
                        configuration.setKeyGenerator(PQCDefaultMLKEMMaterial.keyGenerator);
                        configuration.setKeyPair(PQCDefaultMLKEMMaterial.keyPair);
                        break;
                    default:
                        break;
                }
            }
        }

        // Configure classical KEM material if not already set
        if (ObjectHelper.isEmpty(configuration.getClassicalKeyAgreement())
                && ObjectHelper.isEmpty(configuration.getClassicalKeyPair())) {
            if (ObjectHelper.isNotEmpty(configuration.getClassicalKEMAlgorithm())) {
                String classicalAlg = configuration.getClassicalKEMAlgorithm();
                String pqcAlg = configuration.getKeyEncapsulationAlgorithm();

                // Use pre-built hybrid materials for common combinations
                if ("X25519".equals(classicalAlg) && "MLKEM".equals(pqcAlg)) {
                    configuration.setClassicalKeyAgreement(PQCDefaultX25519MLKEMMaterial.classicalKeyAgreement);
                    configuration.setClassicalKeyPair(PQCDefaultX25519MLKEMMaterial.classicalKeyPair);
                    configuration.setKeyGenerator(PQCDefaultX25519MLKEMMaterial.pqcKeyGenerator);
                    configuration.setKeyPair(PQCDefaultX25519MLKEMMaterial.pqcKeyPair);
                } else if ("ECDH_P256".equals(classicalAlg) && "MLKEM".equals(pqcAlg)) {
                    configuration.setClassicalKeyAgreement(PQCDefaultECDHMLKEMMaterial.classicalKeyAgreement);
                    configuration.setClassicalKeyPair(PQCDefaultECDHMLKEMMaterial.classicalKeyPair);
                    configuration.setKeyGenerator(PQCDefaultECDHMLKEMMaterial.pqcKeyGenerator);
                    configuration.setKeyPair(PQCDefaultECDHMLKEMMaterial.pqcKeyPair);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (keyRotationSchedulerEnabled) {
            KeyLifecycleManager manager = configuration != null ? configuration.getKeyLifecycleManager() : null;
            ObjectHelper.notNull(manager,
                    "keyLifecycleManager (required when keyRotationSchedulerEnabled=true)");
            keyRotationScheduler = new KeyRotationScheduler(manager)
                    .setCheckInterval(Duration.ofMillis(keyRotationCheckInterval))
                    .setMaxKeyAge(keyRotationMaxAge > 0 ? Duration.ofMillis(keyRotationMaxAge) : null)
                    .setMaxKeyUsage(keyRotationMaxUsage);
            keyRotationScheduler.setCamelContext(getCamelContext());
            keyRotationScheduler.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (keyRotationScheduler != null) {
            keyRotationScheduler.stop();
            keyRotationScheduler = null;
        }
        super.doStop();
    }

    public PQCConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(PQCConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean isKeyRotationSchedulerEnabled() {
        return keyRotationSchedulerEnabled;
    }

    /**
     * Whether to start an automated background key rotation scheduler for this component. Requires keyLifecycleManager
     * to be set. The scheduler periodically rotates keys that exceed the configured age and/or usage policy.
     */
    public void setKeyRotationSchedulerEnabled(boolean keyRotationSchedulerEnabled) {
        this.keyRotationSchedulerEnabled = keyRotationSchedulerEnabled;
    }

    public long getKeyRotationCheckInterval() {
        return keyRotationCheckInterval;
    }

    /**
     * Interval between key rotation checks when the scheduler is enabled.
     */
    public void setKeyRotationCheckInterval(long keyRotationCheckInterval) {
        this.keyRotationCheckInterval = keyRotationCheckInterval;
    }

    public long getKeyRotationMaxAge() {
        return keyRotationMaxAge;
    }

    /**
     * When the scheduler is enabled, rotate keys older than this age. If not set, age is not used as a rotation signal.
     */
    public void setKeyRotationMaxAge(long keyRotationMaxAge) {
        this.keyRotationMaxAge = keyRotationMaxAge;
    }

    public long getKeyRotationMaxUsage() {
        return keyRotationMaxUsage;
    }

    /**
     * When the scheduler is enabled, rotate keys whose recorded usage count reaches this value. 0 disables usage-based
     * rotation.
     */
    public void setKeyRotationMaxUsage(long keyRotationMaxUsage) {
        this.keyRotationMaxUsage = keyRotationMaxUsage;
    }

    /**
     * The automated key rotation scheduler created when keyRotationSchedulerEnabled is true, or null.
     */
    public KeyRotationScheduler getKeyRotationScheduler() {
        return keyRotationScheduler;
    }
}
