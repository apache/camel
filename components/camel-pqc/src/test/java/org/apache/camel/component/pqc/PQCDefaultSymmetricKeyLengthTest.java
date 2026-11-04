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
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.dataformat.PQCDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The default KEM is ML-KEM-768, which produces a 256-bit shared secret, so the symmetric key derived from it defaults
 * to 256 bits rather than throwing that margin away.
 */
public class PQCDefaultSymmetricKeyLengthTest extends CamelTestSupport {

    @EndpointInject("mock:encapsulate")
    protected MockEndpoint resultEncapsulate;

    @Produce("direct:encapsulate")
    protected ProducerTemplate templateEncapsulate;

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
                // no symmetricKeyLength: the default is what is under test
                from("direct:encapsulate")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation&keyEncapsulationAlgorithm=MLKEM"
                            + "&symmetricKeyAlgorithm=AES")
                        .to("mock:encapsulate");
            }
        };
    }

    @Test
    void theConfiguredDefaultIs256() {
        assertEquals(256, new PQCConfiguration().getSymmetricKeyLength());
        assertEquals(256, new PQCDataFormat().getSymmetricKeyLength());
    }

    @Test
    void anEncapsulatedKeyIs256BitsByDefault() throws Exception {
        resultEncapsulate.expectedMessageCount(1);

        templateEncapsulate.sendBody("Hello");

        resultEncapsulate.assertIsSatisfied();

        SecretKeyWithEncapsulation key = resultEncapsulate.getExchanges().get(0).getMessage()
                .getBody(SecretKeyWithEncapsulation.class);
        assertEquals(32, key.getEncoded().length, "the derived key must be 256 bits");
    }
}
