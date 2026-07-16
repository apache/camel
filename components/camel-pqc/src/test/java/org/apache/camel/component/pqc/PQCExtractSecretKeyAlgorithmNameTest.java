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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The secret key restored by extractSecretKeyFromEncapsulation must carry the mapped JCE algorithm name, not the raw
 * PQCSymmetricAlgorithms enum name. For GOST3412_2015 the raw name is not a resolvable cipher algorithm at all.
 */
public class PQCExtractSecretKeyAlgorithmNameTest extends CamelTestSupport {

    @EndpointInject("mock:gost")
    protected MockEndpoint resultGost;

    @EndpointInject("mock:desede")
    protected MockEndpoint resultDesede;

    @BeforeAll
    public static void startup() {
        ensureProviders();
    }

    /**
     * PQCEndpoint removes the BouncyCastle providers from the JVM when it stops, so with more than one test method in
     * the class they must be (re-)registered before each CamelContext is built.
     */
    private static void ensureProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:gost")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation"
                            + "&symmetricKeyAlgorithm=GOST3412_2015&symmetricKeyLength=256")
                        .to("pqc:keyenc?operation=extractSecretKeyEncapsulation"
                            + "&symmetricKeyAlgorithm=GOST3412_2015&symmetricKeyLength=256")
                        .to("pqc:keyenc?operation=extractSecretKeyFromEncapsulation"
                            + "&symmetricKeyAlgorithm=GOST3412_2015&symmetricKeyLength=256")
                        .to("mock:gost");

                from("direct:desede")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation"
                            + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192")
                        .to("pqc:keyenc?operation=extractSecretKeyEncapsulation"
                            + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192")
                        .to("pqc:keyenc?operation=extractSecretKeyFromEncapsulation"
                            + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192")
                        .to("mock:desede");
            }
        };
    }

    @Test
    void testGost3412RestoredKeyUsesJceAlgorithmName() throws Exception {
        resultGost.expectedMessageCount(1);
        template.sendBody("direct:gost", "Hello");
        resultGost.assertIsSatisfied();

        SecretKey restored = resultGost.getExchanges().get(0).getMessage().getBody(SecretKey.class);
        assertNotNull(restored);
        // the enum name is GOST3412_2015 but the JCE name is GOST3412-2015
        assertEquals(PQCSymmetricAlgorithms.GOST3412_2015.getAlgorithm(), restored.getAlgorithm());
        assertEquals("GOST3412-2015", restored.getAlgorithm());
        // with the raw enum name this throws NoSuchAlgorithmException, so the restored key was unusable
        assertDoesNotThrow(() -> Cipher.getInstance(restored.getAlgorithm(), BouncyCastleProvider.PROVIDER_NAME));
    }

    @Test
    void testDesedeRestoredKeyUsesJceAlgorithmName() throws Exception {
        resultDesede.expectedMessageCount(1);
        template.sendBody("direct:desede", "Hello");
        resultDesede.assertIsSatisfied();

        SecretKey restored = resultDesede.getExchanges().get(0).getMessage().getBody(SecretKey.class);
        assertNotNull(restored);
        assertEquals(PQCSymmetricAlgorithms.DESEDE.getAlgorithm(), restored.getAlgorithm());
        assertEquals("DESede", restored.getAlgorithm());
        assertDoesNotThrow(() -> Cipher.getInstance(restored.getAlgorithm(), BouncyCastleProvider.PROVIDER_NAME));
    }

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        ensureProviders();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        return kpg.generateKeyPair();
    }

    @BindToRegistry("KeyGenerator")
    public KeyGenerator setKeyGenerator() throws NoSuchAlgorithmException, NoSuchProviderException {
        ensureProviders();
        return KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
    }
}
