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

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A String body must always be encoded as UTF-8 when signing and verifying, never with the JVM default charset, so that
 * a signature produced on one JVM verifies on another with a different default.
 */
public class PQCSignatureCharsetTest extends CamelTestSupport {

    private static final String NON_ASCII = "héllo wörld — ünïcode";

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
                from("direct:signOnly").to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA");
                from("direct:verifyOnly").to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA");
            }
        };
    }

    private byte[] sign(Object body) {
        Exchange out = template.request("direct:signOnly", e -> e.getMessage().setBody(body));
        return out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
    }

    private boolean verify(Object body, byte[] signature) {
        Exchange out = template.request("direct:verifyOnly", e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader(PQCConstants.SIGNATURE, signature);
        });
        return out.getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
    }

    @Test
    void testStringBodyIsInterchangeableWithItsUtf8Bytes() {
        byte[] utf8 = NON_ASCII.getBytes(StandardCharsets.UTF_8);

        byte[] fromString = sign(NON_ASCII);
        assertNotNull(fromString);
        assertTrue(verify(utf8, fromString), "a String body must be signed as UTF-8");

        byte[] fromBytes = sign(utf8);
        assertTrue(verify(NON_ASCII, fromBytes), "a String body must be verified as UTF-8");
    }

    @Test
    void testStringBodyIsNotEncodedWithAnotherCharset() {
        byte[] latin1 = NON_ASCII.getBytes(StandardCharsets.ISO_8859_1);
        // sanity: the two encodings really do differ for this text
        assertFalse(Arrays.equals(latin1, NON_ASCII.getBytes(StandardCharsets.UTF_8)));

        // so a signature over the String must not verify against the ISO-8859-1 bytes
        assertFalse(verify(latin1, sign(NON_ASCII)),
                "a String body must not be encoded with the platform charset (ISO-8859-1 here)");
    }
}
