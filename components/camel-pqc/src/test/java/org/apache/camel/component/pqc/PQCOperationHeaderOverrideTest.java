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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The documented CamelPQCOperation header may only select an operation the endpoint actually holds resources for.
 * Crossing from the signature family to the key encapsulation family used to dereference a null KeyGenerator.
 */
public class PQCOperationHeaderOverrideTest extends CamelTestSupport {

    @EndpointInject("mock:out")
    protected MockEndpoint result;

    @Produce("direct:sign")
    protected ProducerTemplate template;

    @BeforeEach
    void addProviders() {
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
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA")
                        .to("mock:out");
            }
        };
    }

    @Test
    void anOverrideCrossingToTheKemFamilyIsRejectedWithAClearMessage() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:sign", "Hello",
                        PQCConstants.OPERATION, PQCOperations.generateSecretKeyEncapsulation));

        Throwable cause = e.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause,
                "expected a configuration error, not a NullPointerException: " + cause);

        String message = cause.getMessage();
        assertTrue(message.contains("generateSecretKeyEncapsulation"), "must name the requested operation: " + message);
        assertTrue(message.contains("configured for 'sign'"), "must name the configured operation: " + message);
        assertTrue(message.contains("KeyGenerator"), "must name what is missing: " + message);
    }

    @Test
    void anOverrideToTheConfiguredOperationStillWorks() throws Exception {
        result.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:sign", "Hello", PQCConstants.OPERATION, PQCOperations.sign);

        result.assertIsSatisfied();
        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    @Test
    void anOverrideToAnOperationNeedingNoKeyMaterialIsAllowed() {
        // getKeyState needs no key material beyond the key pair, so the signature-only endpoint may serve it: the
        // override is accepted and the operation itself then rejects the non-stateful ML-DSA key
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:sign", "Hello",
                        PQCConstants.OPERATION, PQCOperations.getKeyState));

        Throwable cause = e.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("getKeyState is only supported for stateful signature schemes"),
                "the override must be accepted and fail inside the operation: " + cause.getMessage());
    }

    @Test
    void noHeaderUsesTheConfiguredOperation() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:sign", "Hello");

        result.assertIsSatisfied();
        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }
}
