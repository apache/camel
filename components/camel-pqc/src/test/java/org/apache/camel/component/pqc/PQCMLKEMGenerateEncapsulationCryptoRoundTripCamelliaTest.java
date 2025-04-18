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

import java.security.*;

import javax.crypto.KeyGenerator;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.crypto.CryptoDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PQCMLKEMGenerateEncapsulationCryptoRoundTripCamelliaTest extends CamelTestSupport {

    @EndpointInject("mock:encapsulate")
    protected MockEndpoint resultEncapsulate;

    @Produce("direct:encapsulate")
    protected ProducerTemplate templateEncapsulate;

    @EndpointInject("mock:encrypted")
    protected MockEndpoint resultEncrypted;

    @EndpointInject("mock:unencrypted")
    protected MockEndpoint resultDecrypted;

    public PQCMLKEMGenerateEncapsulationCryptoRoundTripCamelliaTest() throws NoSuchAlgorithmException {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        CryptoDataFormat cryptoFormat = new CryptoDataFormat("CAMELLIA", null);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:encapsulate")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=CAMELLIA")
                        .to("mock:encapsulate")
                        .to("pqc:keyenc?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=CAMELLIA")
                        .to("pqc:keyenc?operation=extractSecretKeyFromEncapsulation&symmetricKeyAlgorithm=CAMELLIA")
                        .setHeader(CryptoDataFormat.KEY, body())
                        .setBody(constant("Hello"))
                        .marshal(cryptoFormat)
                        .log("Encrypted ${body}")
                        .to("mock:encrypted")
                        .unmarshal(cryptoFormat)
                        .log("Unencrypted ${body}")
                        .to("mock:unencrypted");
                ;
            }
        };
    }

    @BeforeAll
    public static void startup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    @Test
    void testSignAndVerify() throws Exception {
        resultEncapsulate.expectedMessageCount(1);
        resultEncrypted.expectedMessageCount(1);
        resultDecrypted.expectedMessageCount(1);
        templateEncapsulate.sendBody("Hello");
        resultEncapsulate.assertIsSatisfied();
        assertNotNull(resultEncapsulate.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class));
        assertEquals(PQCSymmetricAlgorithms.CAMELLIA.getAlgorithm(),
                resultEncapsulate.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class).getAlgorithm());
        assertNotNull(resultEncrypted.getExchanges().get(0).getMessage().getBody());
        assertEquals("Hello", resultDecrypted.getExchanges().get(0).getMessage().getBody(String.class));
    }

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        return kp;
    }

    @BindToRegistry("KeyGenerator")
    public KeyGenerator setKeyGenerator()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyGenerator kg = KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        return kg;
    }
}
