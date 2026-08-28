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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy symmetrically encrypted data packet carries no modification detection code, and OpenPGP's CFB mode is
 * malleable without one. Because the packet type is chosen by whoever produced the message, a decryptor that only
 * checks integrity when the message claims to be protected lets the sender decide whether the check runs at all.
 */
class PGPRequireIntegrityProtectionTest {

    private static final String PUB_KEY_RING = "org/apache/camel/component/crypto/pubring.gpg";
    private static final String SEC_KEY_RING = "org/apache/camel/component/crypto/secring.gpg";
    private static final String PAYLOAD = "Hello PGP";

    @BeforeAll
    static void installProvider() {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void aMessageWithoutIntegrityProtectionIsRejectedByDefault() throws Exception {
        PGPDataFormat decryptor = decryptor();
        assertTrue(decryptor.isRequireIntegrityProtection(), "expected the secure default");

        Exception e = assertThrows(PGPException.class, () -> roundTrip(decryptor));
        assertTrue(e.getMessage().contains("not integrity protected"), "unexpected message: " + e.getMessage());
    }

    @Test
    void theLegacyPacketCanStillBeAcceptedOnPurpose() throws Exception {
        PGPDataFormat decryptor = decryptor();
        decryptor.setRequireIntegrityProtection(false);

        assertEquals(PAYLOAD, roundTrip(decryptor));
    }

    /**
     * Encrypts with {@code integrity=false}, so the message carries no modification detection code, then decrypts it
     * with the given decryptor.
     */
    private static String roundTrip(PGPDataFormat decryptor) throws Exception {
        PGPDataFormat encryptor = new PGPDataFormat();
        encryptor.setKeyFileName(PUB_KEY_RING);
        encryptor.setKeyUserid("sdude");
        encryptor.setIntegrity(false);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            encryptor.start();
            decryptor.start();

            Exchange exchange = new DefaultExchange(context);
            ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
            encryptor.marshal(exchange, PAYLOAD.getBytes(StandardCharsets.UTF_8), encrypted);

            Object body = decryptor.unmarshal(exchange, encrypted.toByteArray());
            return context.getTypeConverter().convertTo(String.class, exchange, body);
        }
    }

    private static PGPDataFormat decryptor() {
        PGPDataFormat decryptor = new PGPDataFormat();
        decryptor.setKeyFileName(SEC_KEY_RING);
        decryptor.setPassword("sdude");
        return decryptor;
    }
}
