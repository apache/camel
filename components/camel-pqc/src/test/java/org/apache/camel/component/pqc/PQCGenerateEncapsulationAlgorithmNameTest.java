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

import javax.crypto.Cipher;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generate and extract KEM operations must agree on the algorithm name stamped on the secret key, and that name
 * must be the JCE one so the key can be handed straight to a Cipher.
 */
public class PQCGenerateEncapsulationAlgorithmNameTest extends CamelTestSupport {

    @EndpointInject("mock:encapsulate")
    protected MockEndpoint resultEncapsulate;

    @EndpointInject("mock:extract")
    protected MockEndpoint resultExtract;

    @Produce("direct:encapsulate")
    protected ProducerTemplate templateEncapsulate;

    @Produce("direct:noAlgorithm")
    protected ProducerTemplate templateNoAlgorithm;

    @BeforeEach
    void addProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:encapsulate")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM"
                            + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192")
                        .to("mock:encapsulate")
                        .to("pqc:keyext?operation=extractSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM"
                            + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192")
                        .to("mock:extract");

                from("direct:noAlgorithm")
                        .to("pqc:nokey?operation=generateSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM");
            }
        };
    }

    @Test
    void generateAndExtractStampTheSameJceName() throws Exception {
        resultEncapsulate.expectedMessageCount(1);
        resultExtract.expectedMessageCount(1);

        templateEncapsulate.sendBody("Hello");

        resultEncapsulate.assertIsSatisfied();
        resultExtract.assertIsSatisfied();

        String generated = resultEncapsulate.getExchanges().get(0).getMessage()
                .getBody(SecretKeyWithEncapsulation.class).getAlgorithm();
        String extracted = resultExtract.getExchanges().get(0).getMessage()
                .getBody(SecretKeyWithEncapsulation.class).getAlgorithm();

        assertEquals(PQCSymmetricAlgorithms.DESEDE.getAlgorithm(), generated,
                "the generated key must carry the mapped JCE name");
        assertEquals(generated, extracted, "both ends of the exchange must agree on the key algorithm");

        // the whole point of the JCE name: the key is directly usable
        assertDoesNotThrow(() -> Cipher.getInstance(generated),
                "the stamped name must resolve as a cipher algorithm");
    }

    @Test
    void aMissingSymmetricAlgorithmIsRejected() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> templateNoAlgorithm.sendBody("Hello"));

        Throwable cause = e.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause, "expected a configuration error but got " + cause);
        assertTrue(cause.getMessage().contains("Symmetric Algorithm"),
                "unexpected message: " + cause.getMessage());
    }
}
