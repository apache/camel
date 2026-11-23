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

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GnuPG 2.4 and later encrypts with the OCB encrypted data packet (packet tag 20) for keys that announce AEAD support,
 * which is every key it makes itself. OCB is authenticated encryption, so such a message is integrity protected and
 * must decrypt with {@code requireIntegrityProtection} on. That a message without integrity protection is still refused
 * is tested in {@link PGPRequireIntegrityProtectionTest}.
 * <p>
 * The key pair and the messages were made with GnuPG 2.5.24, in a throwaway GnuPG home directory:
 *
 * <pre>
 * export GNUPGHOME=$(mktemp -d)
 * gpg --batch --pinentry-mode loopback --passphrase camel-ocb-test \
 *     --quick-gen-key 'Camel OCB Test &lt;ocb-test@example.com&gt;' default default never
 * gpg --batch --export ocb-test@example.com &gt; ocb-pubring.gpg
 * gpg --batch --pinentry-mode loopback --passphrase camel-ocb-test \
 *     --export-secret-keys ocb-test@example.com &gt; ocb-secring.gpg
 *
 * printf 'Hello OCB' &gt; plain.txt
 * gpg --batch --pinentry-mode loopback --passphrase camel-ocb-test --trust-model always \
 *     --recipient ocb-test@example.com --local-user ocb-test@example.com \
 *     --sign --encrypt --output ocb-signed-message.gpg plain.txt
 *
 * perl -e 'print "Hello OCB in chunks. " x 20' &gt; plain-chunks.txt
 * gpg --batch --trust-model always --chunk-size 6 --compress-algo none \
 *     --recipient ocb-test@example.com --encrypt --output ocb-chunked-message.gpg plain-chunks.txt
 * </pre>
 *
 * {@code gpg --list-packets} shows the OCB packet in both messages, the second one in chunks of 64 bytes:
 *
 * <pre>
 * # off=0 ctb=84 tag=1 hlen=2 plen=94
 * :pubkey enc packet: version 3, algo 18, keyid 1FB0A39556A87E1D
 * # off=96 ctb=d4 tag=20 hlen=3 plen=261 new-ctb
 * :ocb encrypted packet: cipher=9 aead=2 cb=16
 * # off=118 ctb=a3 tag=8 hlen=1 plen=0 indeterminate
 * :compressed packet: algo=2
 * # off=120 ctb=90 tag=4 hlen=2 plen=13
 * :onepass_sig packet: keyid 670531E84BC7F033
 * # off=135 ctb=ac tag=11 hlen=2 plen=24
 * :literal data packet:
 * # off=161 ctb=88 tag=2 hlen=2 plen=167
 * :signature packet: algo 22, keyid 670531E84BC7F033
 *
 * # off=0 ctb=84 tag=1 hlen=2 plen=94
 * :pubkey enc packet: version 3, algo 18, keyid 1FB0A39556A87E1D
 * # off=96 ctb=d4 tag=20 hlen=2 plen=0 partial new-ctb
 * :ocb encrypted packet: cipher=9 aead=2 cb=0
 * # off=117 ctb=ad tag=11 hlen=3 plen=442
 * :literal data packet:
 * </pre>
 */
class PGPOcbEncryptedMessageTest {

    private static final String PUB_KEY_RING = "org/apache/camel/component/crypto/ocb-pubring.gpg";
    private static final String SEC_KEY_RING = "org/apache/camel/component/crypto/ocb-secring.gpg";
    private static final String SIGNED_MESSAGE = "org/apache/camel/component/crypto/ocb-signed-message.gpg";
    private static final String CHUNKED_MESSAGE = "org/apache/camel/component/crypto/ocb-chunked-message.gpg";
    private static final String PASSPHRASE = "camel-ocb-test";

    @BeforeAll
    static void installProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void aSignedOcbMessageOfGnuPGDecryptsWithIntegrityProtectionRequired() throws Exception {
        PGPDataFormat decryptor = decryptor();
        assertTrue(decryptor.isRequireIntegrityProtection(), "expected the secure default");
        decryptor.setSignatureKeyFileName(PUB_KEY_RING);
        decryptor.setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_REQUIRED);

        assertEquals("Hello OCB", decrypt(decryptor, read(SIGNED_MESSAGE)));
    }

    @Test
    void anOcbMessageInSeveralChunksDecryptsWithIntegrityProtectionRequired() throws Exception {
        assertEquals("Hello OCB in chunks. ".repeat(20), decrypt(decryptor(), read(CHUNKED_MESSAGE)));
    }

    // the tampered messages are decrypted with requireIntegrityProtection off, so it is the authentication of OCB
    // that rejects them, not the check of the packet type

    @Test
    void anOcbMessageWithAnAlteredChunkIsRejected() throws Exception {
        // the ciphertext begins after the 3 byte packet header and the 19 bytes of version, cipher, AEAD algorithm,
        // chunk size and IV of the OCB packet at offset 96
        byte[] message = read(SIGNED_MESSAGE);
        message[96 + 3 + 19 + 50] ^= 0x01;

        assertThrows(PGPException.class, () -> decrypt(lenientDecryptor(), message));
    }

    @Test
    void anOcbMessageWithAnAlteredFinalTagIsRejected() throws Exception {
        byte[] message = read(SIGNED_MESSAGE);
        message[message.length - 1] ^= 0x01;

        assertThrows(PGPException.class, () -> decrypt(lenientDecryptor(), message));
    }

    @Test
    void anOcbMessageInSeveralChunksWithAnAlteredFinalTagIsRejected() throws Exception {
        byte[] message = read(CHUNKED_MESSAGE);
        message[message.length - 1] ^= 0x01;

        // found while reading the literal data, when the last chunk is decrypted
        assertThrows(IOException.class, () -> decrypt(lenientDecryptor(), message));
    }

    private static PGPDataFormat decryptor() {
        PGPDataFormat decryptor = new PGPDataFormat();
        decryptor.setKeyFileName(SEC_KEY_RING);
        decryptor.setPassword(PASSPHRASE);
        return decryptor;
    }

    private static PGPDataFormat lenientDecryptor() {
        PGPDataFormat decryptor = decryptor();
        decryptor.setRequireIntegrityProtection(false);
        return decryptor;
    }

    private static byte[] read(String resource) throws IOException {
        try (InputStream in = PGPOcbEncryptedMessageTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "missing test resource " + resource);
            return in.readAllBytes();
        }
    }

    private static String decrypt(PGPDataFormat decryptor, byte[] message) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            decryptor.start();

            Exchange exchange = new DefaultExchange(context);
            Object body = decryptor.unmarshal(exchange, message);
            return context.getTypeConverter().convertTo(String.class, exchange, body);
        }
    }
}
