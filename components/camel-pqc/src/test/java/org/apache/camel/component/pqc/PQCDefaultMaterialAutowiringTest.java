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

import java.security.Security;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every constant of the algorithm enums must get default key material autowired from the URI alone. Three of them (HSS,
 * XMSSMT, SNTRUPrime) fell through the component's switch, so their documented default material class was dead code and
 * the route only failed on the first exchange.
 */
public class PQCDefaultMaterialAutowiringTest {

    @BeforeEach
    void addProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @ParameterizedTest
    @EnumSource(PQCSignatureAlgorithms.class)
    void everySignatureAlgorithmGetsDefaultMaterial(PQCSignatureAlgorithms algorithm) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.setAutowiredEnabled(false);
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:sign?operation=sign&signatureAlgorithm=" + algorithm.name(), PQCEndpoint.class);

            assertNotNull(endpoint.getConfiguration().getKeyPair(),
                    "no default key pair autowired for signatureAlgorithm=" + algorithm.name());
            assertNotNull(endpoint.getConfiguration().getSigner(),
                    "no default signer autowired for signatureAlgorithm=" + algorithm.name());
        }
    }

    @ParameterizedTest
    @EnumSource(PQCKeyEncapsulationAlgorithms.class)
    void everyKemAlgorithmGetsDefaultMaterial(PQCKeyEncapsulationAlgorithms algorithm) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.setAutowiredEnabled(false);
            context.start();

            PQCEndpoint endpoint = context.getEndpoint(
                    "pqc:kem?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES"
                                                       + "&keyEncapsulationAlgorithm=" + algorithm.name(),
                    PQCEndpoint.class);

            assertNotNull(endpoint.getConfiguration().getKeyPair(),
                    "no default key pair autowired for keyEncapsulationAlgorithm=" + algorithm.name());
            assertNotNull(endpoint.getConfiguration().getKeyGenerator(),
                    "no default key generator autowired for keyEncapsulationAlgorithm=" + algorithm.name());
        }
    }
}
