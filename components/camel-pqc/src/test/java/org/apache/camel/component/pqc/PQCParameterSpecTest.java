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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PQCParameterSpecTest extends CamelTestSupport {

    @EndpointInject("mock:verify")
    protected MockEndpoint resultVerify;

    @BeforeAll
    public static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // sign and verify use the same algorithm + parameterSpec, so they must share the same key
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_87")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_87")
                        .to("mock:verify");
            }
        };
    }

    private int publicKeyLength(String uri) {
        PQCEndpoint endpoint = context.getEndpoint(uri, PQCEndpoint.class);
        return endpoint.getConfiguration().getKeyPair().getPublic().getEncoded().length;
    }

    @Test
    void testParameterSpecDrivesSignatureKeySize() {
        int pk44 = publicKeyLength("pqc:a?operation=sign&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_44");
        int pk65 = publicKeyLength("pqc:b?operation=sign&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_65");
        int pk87 = publicKeyLength("pqc:c?operation=sign&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_87");
        assertTrue(pk44 < pk65, "ml_dsa_44 public key should be smaller than ml_dsa_65");
        assertTrue(pk65 < pk87, "ml_dsa_65 public key should be smaller than ml_dsa_87");
    }

    @Test
    void testParameterSpecDrivesKemKeySize() {
        int pk512 = publicKeyLength("pqc:k1?operation=generateSecretKeyEncapsulation"
                                    + "&keyEncapsulationAlgorithm=MLKEM&parameterSpec=ml_kem_512");
        int pk1024 = publicKeyLength("pqc:k2?operation=generateSecretKeyEncapsulation"
                                     + "&keyEncapsulationAlgorithm=MLKEM&parameterSpec=ml_kem_1024");
        assertTrue(pk512 < pk1024, "ml_kem_512 public key should be smaller than ml_kem_1024");
    }

    @Test
    void testEndpointsShareTheKeyForTheSameParameterSpec() {
        PQCEndpoint sign = context.getEndpoint(
                "pqc:s1?operation=sign&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_44", PQCEndpoint.class);
        PQCEndpoint verify = context.getEndpoint(
                "pqc:s2?operation=verify&signatureAlgorithm=MLDSA&parameterSpec=ml_dsa_44", PQCEndpoint.class);
        assertArrayEquals(sign.getConfiguration().getKeyPair().getPublic().getEncoded(),
                verify.getConfiguration().getKeyPair().getPublic().getEncoded(),
                "endpoints with the same algorithm and parameterSpec must share the same key");
    }

    @Test
    void testSignVerifyWithNonDefaultParameterSpec() throws Exception {
        resultVerify.expectedMessageCount(1);
        template.sendBody("direct:sign", "hello ml-dsa-87");
        resultVerify.assertIsSatisfied();
        assertTrue(resultVerify.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class),
                "a signature made with a non-default parameter set should verify");
    }

    @Test
    void testUnsupportedAlgorithmIsRejected() {
        // XMSS has no name-addressable parameter spec
        assertThrows(Exception.class,
                () -> context.getEndpoint("pqc:x?operation=sign&signatureAlgorithm=XMSS&parameterSpec=whatever",
                        PQCEndpoint.class));
    }

    @Test
    void testHybridOperationIsRejected() {
        assertThrows(Exception.class,
                () -> context.getEndpoint(
                        "pqc:h?operation=hybridSign&signatureAlgorithm=MLDSA&classicalSignatureAlgorithm=ECDSA_P256"
                                          + "&parameterSpec=ml_dsa_87",
                        PQCEndpoint.class));
    }
}
