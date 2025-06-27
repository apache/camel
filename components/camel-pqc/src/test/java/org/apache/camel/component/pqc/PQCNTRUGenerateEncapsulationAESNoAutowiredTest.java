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

import java.security.NoSuchAlgorithmException;
import java.security.Security;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PQCNTRUGenerateEncapsulationAESNoAutowiredTest extends CamelTestSupport {

    @EndpointInject("mock:encapsulate")
    protected MockEndpoint resultEncapsulate;

    @Produce("direct:encapsulate")
    protected ProducerTemplate templateEncapsulate;

    @EndpointInject("mock:extract")
    protected MockEndpoint resultExtract;

    public PQCNTRUGenerateEncapsulationAESNoAutowiredTest() throws NoSuchAlgorithmException {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:encapsulate").to(
                        "pqc:keyenc?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES&keyEncapsulationAlgorithm=NTRU")
                        .to("mock:encapsulate")
                        .to("pqc:keyenc?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=AES&keyEncapsulationAlgorithm=NTRU")
                        .to("mock:extract");
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
        resultExtract.expectedMessageCount(1);
        templateEncapsulate.sendBody("Hello");
        resultEncapsulate.assertIsSatisfied();
        assertNotNull(resultEncapsulate.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class));
        assertEquals(PQCSymmetricAlgorithms.AES.getAlgorithm(),
                resultEncapsulate.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class).getAlgorithm());
        SecretKeyWithEncapsulation secEncrypted
                = resultEncapsulate.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class);
        assertNotNull(resultExtract.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class));
        assertEquals(PQCSymmetricAlgorithms.AES.getAlgorithm(),
                resultExtract.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class).getAlgorithm());
        SecretKeyWithEncapsulation secEncryptedExtracted
                = resultExtract.getExchanges().get(0).getMessage().getBody(SecretKeyWithEncapsulation.class);
        assertTrue(Arrays.areEqual(secEncrypted.getEncoded(), secEncryptedExtracted.getEncoded()));
    }
}
