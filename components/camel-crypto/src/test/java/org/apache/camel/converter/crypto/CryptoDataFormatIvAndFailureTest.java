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
package org.apache.camel.converter.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.KeyGenerator;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoDataFormatIvAndFailureTest {

    private static final String PAYLOAD = "the quick brown fox jumps over the lazy dog";

    /**
     * Inlining exists so the initialization vector travels with the message. Requiring a statically configured one as
     * well is what pushed routes into reusing a single vector for every message.
     */
    @Test
    void inliningGeneratesAFreshInitializationVectorPerMessage() throws Exception {
        Key key = key();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            CryptoDataFormat encryptor = new CryptoDataFormat("AES/CBC/PKCS5Padding", key);
            encryptor.setShouldInlineInitializationVector(true);

            byte[] first = marshal(context, encryptor, PAYLOAD);
            byte[] second = marshal(context, encryptor, PAYLOAD);

            assertFalse(java.util.Arrays.equals(first, second),
                    "the same plaintext must not produce identical ciphertext twice");

            CryptoDataFormat decryptor = new CryptoDataFormat("AES/CBC/PKCS5Padding", key);
            decryptor.setShouldInlineInitializationVector(true);
            assertEquals(PAYLOAD, unmarshal(context, decryptor, first));
            assertEquals(PAYLOAD, unmarshal(context, decryptor, second));
        }
    }

    /**
     * A caller who can submit ciphertext and observe the outcome must not be able to tell a padding failure from a MAC
     * failure - telling them apart is what turns CBC decryption into a padding oracle.
     */
    @Test
    void badPaddingAndBadMacAreReportedIdentically() throws Exception {
        Key key = key();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            // a static vector, not inlining, so this exercises the failure reporting and nothing else
            CryptoDataFormat format = new CryptoDataFormat("AES/CBC/PKCS5Padding", key);
            format.setInitVector(new byte[16]);

            byte[] ciphertext = marshal(context, format, PAYLOAD);

            // corrupt the last byte: the final block no longer decrypts to valid padding
            byte[] badPadding = ciphertext.clone();
            badPadding[badPadding.length - 1] ^= 0x01;

            // corrupt a byte in the middle: padding still validates, the appended MAC does not
            byte[] badMac = ciphertext.clone();
            badMac[badMac.length / 2] ^= 0x01;

            String paddingFailure = failureMessage(context, format, badPadding);
            String macFailure = failureMessage(context, format, badMac);

            assertEquals(macFailure, paddingFailure, "the two failures must be indistinguishable");
            assertTrue(paddingFailure.contains("authentication failed"), "unexpected message: " + paddingFailure);
        }
    }

    /**
     * The inlined length is read from the message and used to size an allocation, so it has to be bounded.
     */
    @Test
    void anOversizedInlinedInitializationVectorLengthIsRejected() throws Exception {
        Key key = key();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            CryptoDataFormat decryptor = new CryptoDataFormat("AES/CBC/PKCS5Padding", key);
            decryptor.setShouldInlineInitializationVector(true);

            // a four byte length of 0x7FFFFFFF followed by nothing
            byte[] hostile = { 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

            Exception e = assertThrows(Exception.class, () -> unmarshal(context, decryptor, hostile));
            assertTrue(rootMessage(e).contains("is not between 0 and"), "unexpected message: " + rootMessage(e));
        }
    }

    @Test
    void aRoundTripWithAStaticVectorStillWorks() throws Exception {
        Key key = key();
        byte[] iv = new byte[16];
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            CryptoDataFormat format = new CryptoDataFormat("AES/CBC/PKCS5Padding", key);
            format.setInitVector(iv);

            byte[] ciphertext = marshal(context, format, PAYLOAD);
            assertEquals(PAYLOAD, unmarshal(context, format, ciphertext));
            assertArrayEquals(iv, format.getInitVector());
        }
    }

    private static String failureMessage(DefaultCamelContext context, CryptoDataFormat format, byte[] body) {
        Exception e = assertThrows(Exception.class, () -> unmarshal(context, format, body));
        return rootMessage(e);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }

    private static byte[] marshal(DefaultCamelContext context, CryptoDataFormat format, String payload)
            throws Exception {
        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        format.marshal(exchange, payload.getBytes(StandardCharsets.UTF_8), out);
        return out.toByteArray();
    }

    private static String unmarshal(DefaultCamelContext context, CryptoDataFormat format, byte[] body)
            throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Object result = format.unmarshal(exchange, new java.io.ByteArrayInputStream(body));
        return context.getTypeConverter().convertTo(String.class, exchange, result);
    }

    private static Key key() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }
}
