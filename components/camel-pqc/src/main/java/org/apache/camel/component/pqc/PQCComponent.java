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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pqc.crypto.*;
import org.apache.camel.component.pqc.crypto.hybrid.*;
import org.apache.camel.component.pqc.crypto.kem.*;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * For working with Post Quantum Cryptography Algorithms
 */
@Component("pqc")
public class PQCComponent extends HealthCheckComponent {

    @Metadata
    private PQCConfiguration configuration = new PQCConfiguration();

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

    public PQCConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(PQCConfiguration configuration) {
        this.configuration = configuration;
    }
}
