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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For working with Post Quantum Cryptography Algorithms
 */
@Component("pqc")
public class PQCComponent extends HealthCheckComponent {

    private static final Logger LOG = LoggerFactory.getLogger(PQCComponent.class);

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

        if ("CMCE".equals(configuration.getKeyEncapsulationAlgorithm())) {
            LOG.warn("The CMCE (Classic McEliece) key encapsulation algorithm is deprecated: it is believed to be unsafe"
                     + " to use and will be removed in a future release. Use MLKEM instead. Endpoint: {}",
                    endpoint);
        }

        // Captured here, before any auto-configuration runs. The generic blocks below fill the PQC material in, so
        // asking the same question later would always answer "present" and the matched hybrid material would never
        // be applied.
        boolean pqcSignatureMaterialSupplied = ObjectHelper.isNotEmpty(configuration.getSigner())
                || ObjectHelper.isNotEmpty(configuration.getKeyPair());
        boolean pqcKemMaterialSupplied = ObjectHelper.isNotEmpty(configuration.getKeyGenerator())
                || ObjectHelper.isNotEmpty(configuration.getKeyPair());

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
                        configuration.setSigner(PQCDefaultMLDSAMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultMLDSAMaterial.getKeyPair());
                        break;
                    case "SLHDSA":
                        configuration.setSigner(PQCDefaultSLHDSAMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultSLHDSAMaterial.getKeyPair());
                        break;
                    case "LMS":
                        configuration.setSigner(PQCDefaultLMSMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultLMSMaterial.getKeyPair());
                        break;
                    case "XMSS":
                        configuration.setSigner(PQCDefaultXMSSMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultXMSSMaterial.getKeyPair());
                        break;
                    case "XMSSMT":
                        configuration.setSigner(PQCDefaultXMSSMTMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultXMSSMTMaterial.getKeyPair());
                        break;
                    case "HSS":
                        configuration.setSigner(PQCDefaultHSSMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultHSSMaterial.getKeyPair());
                        break;
                    case "FALCON":
                        configuration.setSigner(PQCDefaultFalconMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultFalconMaterial.getKeyPair());
                        break;
                    case "SNOVA":
                        configuration.setSigner(PQCDefaultSNOVAMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultSNOVAMaterial.getKeyPair());
                        break;
                    case "MAYO":
                        configuration.setSigner(PQCDefaultMAYOMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultMAYOMaterial.getKeyPair());
                        break;
                    case "DILITHIUM":
                        configuration.setSigner(PQCDefaultDILITHIUMMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultDILITHIUMMaterial.getKeyPair());
                        break;
                    case "SPHINCSPLUS":
                        configuration.setSigner(PQCDefaultSPHINCSPLUSMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultSPHINCSPLUSMaterial.getKeyPair());
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
                        configuration.setKeyGenerator(PQCDefaultMLKEMMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultMLKEMMaterial.getKeyPair());
                        break;
                    case "BIKE":
                        configuration.setKeyGenerator(PQCDefaultBIKEMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultBIKEMaterial.getKeyPair());
                        break;
                    case "HQC":
                        configuration.setKeyGenerator(PQCDefaultHQCMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultHQCMaterial.getKeyPair());
                        break;
                    case "CMCE":
                        configuration.setKeyGenerator(PQCDefaultCMCEMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultCMCEMaterial.getKeyPair());
                        break;
                    case "SABER":
                        configuration.setKeyGenerator(PQCDefaultSABERMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultSABERMaterial.getKeyPair());
                        break;
                    case "FRODO":
                        configuration.setKeyGenerator(PQCDefaultFRODOMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultFRODOMaterial.getKeyPair());
                        break;
                    case "NTRU":
                        configuration.setKeyGenerator(PQCDefaultNTRUMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultNTRUMaterial.getKeyPair());
                        break;
                    case "NTRULPRime":
                        configuration.setKeyGenerator(PQCDefaultNTRULPRimeMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultNTRULPRimeMaterial.getKeyPair());
                        break;
                    case "SNTRUPrime":
                        configuration.setKeyGenerator(PQCDefaultSNTRUPrimeMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultSNTRUPrimeMaterial.getKeyPair());
                        break;
                    case "KYBER":
                        configuration.setKeyGenerator(PQCDefaultKYBERMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultKYBERMaterial.getKeyPair());
                        break;
                    default:
                        break;
                }
            }
        }

        // Auto-configure hybrid signature materials
        configureHybridSignatureMaterial(configuration, pqcSignatureMaterialSupplied);

        // Auto-configure hybrid KEM materials
        configureHybridKEMMaterial(configuration, pqcKemMaterialSupplied);

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

        BouncyCastleSupport.ensureProviders();

        String spec = configuration.getParameterSpec();
        String signatureAlgorithm = configuration.getSignatureAlgorithm();
        String kemAlgorithm = configuration.getKeyEncapsulationAlgorithm();

        if (ObjectHelper.isNotEmpty(signatureAlgorithm)) {
            PQCSignatureAlgorithms algorithm = PQCSignatureAlgorithms.valueOf(signatureAlgorithm);
            configuration.setKeyPair(
                    resolveKeyPair(signatureAlgorithm, spec, algorithm.getAlgorithm(),
                            algorithm.getBcProvider("KeyPairGenerator")));
            if (ObjectHelper.isEmpty(configuration.getSigner())) {
                configuration.setSigner(Signature.getInstance(algorithm.getAlgorithm(), algorithm.getBcProvider("Signature")));
            }
        } else if (ObjectHelper.isNotEmpty(kemAlgorithm)) {
            PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
            configuration.setKeyPair(
                    resolveKeyPair(kemAlgorithm, spec, algorithm.getAlgorithm(), algorithm.getBcProvider("KeyPairGenerator")));
            if (ObjectHelper.isEmpty(configuration.getKeyGenerator())) {
                configuration.setKeyGenerator(
                        KeyGenerator.getInstance(algorithm.getAlgorithm(), algorithm.getBcProvider("KeyGenerator")));
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
    private void configureHybridSignatureMaterial(PQCConfiguration configuration, boolean pqcMaterialSupplied)
            throws Exception {
        // Only configure if hybrid signature operations and classical algorithm is specified
        if (configuration.getOperation() != PQCOperations.hybridSign
                && configuration.getOperation() != PQCOperations.hybridVerify) {
            return;
        }

        // Configure PQC signature material if not already set
        if (!pqcMaterialSupplied) {
            if (ObjectHelper.isNotEmpty(configuration.getSignatureAlgorithm())) {
                switch (configuration.getSignatureAlgorithm()) {
                    case "MLDSA":
                        configuration.setSigner(PQCDefaultMLDSAMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultMLDSAMaterial.getKeyPair());
                        break;
                    case "SLHDSA":
                        configuration.setSigner(PQCDefaultSLHDSAMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultSLHDSAMaterial.getKeyPair());
                        break;
                    case "DILITHIUM":
                        configuration.setSigner(PQCDefaultDILITHIUMMaterial.getSigner());
                        configuration.setKeyPair(PQCDefaultDILITHIUMMaterial.getKeyPair());
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
                    configuration.setClassicalSigner(PQCDefaultECDSAMLDSAMaterial.getClassicalSigner());
                    configuration.setClassicalKeyPair(PQCDefaultECDSAMLDSAMaterial.getClassicalKeyPair());
                    if (!pqcMaterialSupplied) {
                        configuration.setSigner(PQCDefaultECDSAMLDSAMaterial.getPqcSigner());
                        configuration.setKeyPair(PQCDefaultECDSAMLDSAMaterial.getPqcKeyPair());
                    }
                } else if ("ED25519".equals(classicalAlg) && "MLDSA".equals(pqcAlg)) {
                    configuration.setClassicalSigner(PQCDefaultEd25519MLDSAMaterial.getClassicalSigner());
                    configuration.setClassicalKeyPair(PQCDefaultEd25519MLDSAMaterial.getClassicalKeyPair());
                    if (!pqcMaterialSupplied) {
                        configuration.setSigner(PQCDefaultEd25519MLDSAMaterial.getPqcSigner());
                        configuration.setKeyPair(PQCDefaultEd25519MLDSAMaterial.getPqcKeyPair());
                    }
                }
            }
        }
    }

    /**
     * Auto-configures hybrid KEM materials based on classical and PQC algorithm settings.
     */
    private void configureHybridKEMMaterial(PQCConfiguration configuration, boolean pqcMaterialSupplied) throws Exception {
        // Only configure if hybrid KEM operations and classical algorithm is specified
        if (configuration.getOperation() != PQCOperations.hybridGenerateSecretKeyEncapsulation
                && configuration.getOperation() != PQCOperations.hybridExtractSecretKeyEncapsulation
                && configuration.getOperation() != PQCOperations.hybridExtractSecretKeyFromEncapsulation) {
            return;
        }

        // Configure PQC KEM material if not already set
        if (!pqcMaterialSupplied) {
            if (ObjectHelper.isNotEmpty(configuration.getKeyEncapsulationAlgorithm())) {
                switch (configuration.getKeyEncapsulationAlgorithm()) {
                    case "MLKEM":
                        configuration.setKeyGenerator(PQCDefaultMLKEMMaterial.getKeyGenerator());
                        configuration.setKeyPair(PQCDefaultMLKEMMaterial.getKeyPair());
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
                    configuration.setClassicalKeyAgreement(PQCDefaultX25519MLKEMMaterial.getClassicalKeyAgreement());
                    configuration.setClassicalKeyPair(PQCDefaultX25519MLKEMMaterial.getClassicalKeyPair());
                    if (!pqcMaterialSupplied) {
                        configuration.setKeyGenerator(PQCDefaultX25519MLKEMMaterial.getPqcKeyGenerator());
                        configuration.setKeyPair(PQCDefaultX25519MLKEMMaterial.getPqcKeyPair());
                    }
                } else if ("ECDH_P256".equals(classicalAlg) && "MLKEM".equals(pqcAlg)) {
                    configuration.setClassicalKeyAgreement(PQCDefaultECDHMLKEMMaterial.getClassicalKeyAgreement());
                    configuration.setClassicalKeyPair(PQCDefaultECDHMLKEMMaterial.getClassicalKeyPair());
                    if (!pqcMaterialSupplied) {
                        configuration.setKeyGenerator(PQCDefaultECDHMLKEMMaterial.getPqcKeyGenerator());
                        configuration.setKeyPair(PQCDefaultECDHMLKEMMaterial.getPqcKeyPair());
                    }
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
