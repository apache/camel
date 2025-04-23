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
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultBIKEMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultCMCEMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultFRODOMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultHQCMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultMLKEMMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRULPRimeMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRUMaterial;
import org.apache.camel.component.pqc.crypto.kem.PQCDefaultSABERMaterial;
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
                    case "RAINBOW":
                        configuration.setSigner(PQCDefaultRainbowMaterial.signer);
                        configuration.setKeyPair(PQCDefaultRainbowMaterial.keyPair);
                        break;
                    default:
                        break;
                }
            }
        }

        if (ObjectHelper.isEmpty(configuration.getKeyGenerator()) && ObjectHelper.isEmpty(configuration.getKeyPair())) {
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
                    default:
                        break;
                }
            }
        }

        return endpoint;
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
