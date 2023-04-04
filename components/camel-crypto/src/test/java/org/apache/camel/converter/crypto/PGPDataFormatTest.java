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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class PGPDataFormatTest extends AbstractPGPDataFormatTest {

    private static final String PUB_KEY_RING_SUBKEYS_FILE_NAME = "org/apache/camel/component/crypto/pubringSubKeys.gpg";
    private static final String SEC_KEY_RING_FILE_NAME = "org/apache/camel/component/crypto/secring.gpg";
    private static final String PUB_KEY_RING_FILE_NAME = "org/apache/camel/component/crypto/pubring.gpg";

    PGPDataFormat encryptor = new PGPDataFormat();
    PGPDataFormat decryptor = new PGPDataFormat();

    @BeforeEach
    public void setUpEncryptorAndDecryptor() {

        // the following keyring contains a primary key with KeyFlag "Certify" and a subkey for signing and a subkey for encryption
        encryptor.setKeyFileName(PUB_KEY_RING_SUBKEYS_FILE_NAME);
        encryptor.setSignatureKeyFileName("org/apache/camel/component/crypto/secringSubKeys.gpg");
        encryptor.setSignaturePassword("Abcd1234");
        encryptor.setKeyUserid("keyflag");
        encryptor.setSignatureKeyUserid("keyflag");
        encryptor.setIntegrity(false);
        encryptor.setFileName("fileNameABC");

        // the following keyring contains a primary key with KeyFlag "Certify" and a subkey for signing and a subkey for encryption
        decryptor.setKeyFileName("org/apache/camel/component/crypto/secringSubKeys.gpg");
        decryptor.setSignatureKeyFileName(PUB_KEY_RING_SUBKEYS_FILE_NAME);
        decryptor.setPassword("Abcd1234");
        decryptor.setSignatureKeyUserid("keyflag");
    }

    protected String getKeyFileName() {
        return PUB_KEY_RING_FILE_NAME;
    }

    protected String getKeyFileNameSec() {
        return SEC_KEY_RING_FILE_NAME;
    }

    protected String getKeyUserId() {
        return "sdude@nowhere.net";
    }

    protected List<String> getKeyUserIds() {
        List<String> userids = new ArrayList<>(2);
        userids.add("second");
        userids.add(getKeyUserId());
        return userids;
    }

    protected List<String> getSignatureKeyUserIds() {
        List<String> userids = new ArrayList<>(2);
        userids.add("second");
        userids.add(getKeyUserId());
        return userids;
    }

    protected String getKeyPassword() {
        return "sdude";
    }

    protected String getProvider() {
        return "BC";
    }

    protected int getAlgorithm() {
        return SymmetricKeyAlgorithmTags.TRIPLE_DES;
    }

    protected int getHashAlgorithm() {
        return HashAlgorithmTags.SHA256;
    }

    protected int getCompressionAlgorithm() {
        return CompressionAlgorithmTags.BZIP2;
    }

    @Test
    void testEncryption() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:inline"));
    }

    @Test
    void testEncryption2() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:inline2"));
    }

    @Test
    void testEncryptionArmor() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:inline-armor"));
    }

    @Test
    void testEncryptionSigned() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:inline-sign"));
    }

    @Test
    void testEncryptionKeyRingByteArray() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:key-ring-byte-array"));
    }

    @Test
    void testEncryptionSignedKeyRingByteArray() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:sign-key-ring-byte-array"));
    }

    @Test
    void testSeveralSignerKeys() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:several-signer-keys"));
    }

    @Test
    void testOneUserIdWithSeveralKeys() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:one-userid-several-keys"));
    }

    @Test
    void testKeyAccess() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:key_access"));
    }

    @Test
    void testVerifyExceptionNoPublicKeyFoundCorrespondingToSignatureUserIds() throws Exception {
        setupExpectations(context, 1, "mock:encrypted");
        MockEndpoint exception = setupExpectations(context, 1, "mock:exception");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        Map<String, Object> headers = getHeaders();
        template.sendBodyAndHeaders("direct:verify_exception_sig_userids", payload, headers);
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(exception, IllegalArgumentException.class, null, "No public key found for the key ID(s)");

    }

    @Test
    void testVerifyExceptionNoPassphraseSpecifiedForSignatureKeyUserId() throws Exception {
        MockEndpoint exception = setupExpectations(context, 1, "mock:exception");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        Map<String, Object> headers = new HashMap<>();
        // add signature user id which does not have a passphrase
        headers.put(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID, "userIDWithNoPassphrase");
        // the following entry is necessary for the dynamic test
        headers.put(PGPKeyAccessDataFormat.KEY_USERID, "second");
        template.sendBodyAndHeaders("direct:several-signer-keys", payload, headers);
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(exception, IllegalArgumentException.class, null,
                "No passphrase specified for signature key user ID");
    }

    /**
     * You get three keys with the UserId "keyflag", a primary key and its two sub-keys. The sub-key with KeyFlag
     * {@link KeyFlags#SIGN_DATA} should be used for signing and the sub-key with KeyFlag {@link KeyFlags#ENCRYPT_COMMS}
     * or {@link KeyFlags#ENCRYPT_COMMS} or {@link KeyFlags#ENCRYPT_STORAGE} should be used for decryption.
     *
     * @throws Exception
     */
    @Test
    void testKeyFlagSelectsCorrectKey() throws Exception {
        MockEndpoint mockKeyFlag = getMockEndpoint("mock:encrypted_keyflag");
        mockKeyFlag.setExpectedMessageCount(1);
        template.sendBody("direct:keyflag", "Test Message");
        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> exchanges = mockKeyFlag.getExchanges();
        assertEquals(1, exchanges.size());
        Exchange exchange = exchanges.get(0);
        Message inMess = exchange.getIn();
        assertNotNull(inMess);
        // must contain exactly one encryption key and one signature
        assertEquals(1, inMess.getHeader(PGPKeyAccessDataFormat.NUMBER_OF_ENCRYPTION_KEYS));
        assertEquals(1, inMess.getHeader(PGPKeyAccessDataFormat.NUMBER_OF_SIGNING_KEYS));
    }

    /**
     * You get three keys with the UserId "keyflag", a primary key and its two sub-keys. The sub-key with KeyFlag
     * {@link KeyFlags#SIGN_DATA} should be used for signing and the sub-key with KeyFlag {@link KeyFlags#ENCRYPT_COMMS}
     * or {@link KeyFlags#ENCRYPT_COMMS} or {@link KeyFlags#ENCRYPT_STORAGE} should be used for decryption.
     * <p>
     * Tests also the decryption and verifying part with the subkeys.
     *
     * @throws Exception
     */
    @Test
    void testDecryptVerifyWithSubkey() throws Exception {
        // do not use doRoundTripEncryptionTests("direct:subkey"); because otherwise you get an error in the dynamic test
        String payload = "Test Message";
        MockEndpoint mockSubkey = getMockEndpoint("mock:unencrypted");
        mockSubkey.expectedBodiesReceived(payload);
        template.sendBody("direct:subkey", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testEmptyBody() throws Exception {
        String payload = "";
        MockEndpoint mockSubkey = getMockEndpoint("mock:unencrypted");
        mockSubkey.expectedBodiesReceived(payload);
        template.sendBody("direct:subkey", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testExceptionDecryptorIncorrectInputFormatNoPGPMessage() throws Exception {
        String payload = "Not Correct Format";
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkeyUnmarshal", payload);
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, IllegalArgumentException.class, null, "The input message body has an invalid format.");
    }

    @Test
    void testExceptionDecryptorIncorrectInputFormatPGPSignedData() throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        createSignature(bos);
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkeyUnmarshal", bos.toByteArray());
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, IllegalArgumentException.class, null, "The input message body has an invalid format.");
    }

    @Test
    void testEncryptSignWithoutCompressedDataPacket() {
        assertDoesNotThrow(() -> doRoundTripEncryptionTests("direct:encrypt-sign-without-compressed-data-packet"));
        //        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //
        ////        createEncryptedNonCompressedData(bos, PUB_KEY_RING_SUBKEYS_FILE_NAME);
        //
        //        MockEndpoint mock = getMockEndpoint("mock:exception");
        //        mock.expectedMessageCount(1);
        //        template.sendBody("direct:encrypt-sign-without-compressed-data-packet", bos.toByteArray());
        //        assertMockEndpointsSatisfied();
        //
        //        //checkThrownException(mock, IllegalArgumentException.class, null, "The input message body has an invalid format.");
    }

    @Test
    void testExceptionDecryptorNoKeyFound() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        createEncryptedNonCompressedData(bos, PUB_KEY_RING_FILE_NAME);

        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkeyUnmarshal", bos.toByteArray());
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, PGPException.class, null,
                "PGP message is encrypted with a key which could not be found in the Secret Keyring");
    }

    void createEncryptedNonCompressedData(ByteArrayOutputStream bos, String keyringPath)
            throws Exception {
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5)
                        .setSecureRandom(new SecureRandom()).setProvider(getProvider()));
        encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(readPublicKey(keyringPath)));
        OutputStream encOut = encGen.open(bos, new byte[512]);
        PGPLiteralDataGenerator litData = new PGPLiteralDataGenerator();
        OutputStream litOut = litData.open(encOut, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, new Date(), new byte[512]);

        try {
            litOut.write("Test Message Without Compression".getBytes("UTF-8"));
            litOut.flush();
        } finally {
            IOHelper.close(litOut);
            IOHelper.close(encOut, bos);
        }
    }

    private void createSignature(OutputStream out) throws Exception {
        PGPSecretKey pgpSec = readSecretKey();
        PGPPrivateKey pgpPrivKey
                = pgpSec.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(getProvider()).build(
                        "sdude".toCharArray()));
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(
                        pgpSec.getPublicKey().getAlgorithm(),
                        HashAlgorithmTags.SHA1).setProvider(getProvider()));

        sGen.init(PGPSignature.BINARY_DOCUMENT, pgpPrivKey);

        BCPGOutputStream bOut = new BCPGOutputStream(out);

        InputStream fIn = new ByteArrayInputStream("Test Signature".getBytes("UTF-8"));

        int ch;
        while ((ch = fIn.read()) >= 0) {
            sGen.update((byte) ch);
        }

        fIn.close();

        sGen.generate().encode(bOut);

    }

    static PGPSecretKey readSecretKey() throws Exception {
        InputStream input = new ByteArrayInputStream(getSecKeyRing());
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(input),
                new BcKeyFingerprintCalculator());

        @SuppressWarnings("rawtypes")
        Iterator keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = (PGPSecretKeyRing) keyRingIter.next();

            @SuppressWarnings("rawtypes")
            Iterator keyIter = keyRing.getSecretKeys();
            while (keyIter.hasNext()) {
                PGPSecretKey key = (PGPSecretKey) keyIter.next();

                if (key.isSigningKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find signing key in key ring.");
    }

    static PGPPublicKey readPublicKey(String keyringPath) throws Exception {
        InputStream input = new ByteArrayInputStream(getKeyRing(keyringPath));
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(input),
                new BcKeyFingerprintCalculator());

        @SuppressWarnings("rawtypes")
        Iterator keyRingIter = pgpPub.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPPublicKeyRing keyRing = (PGPPublicKeyRing) keyRingIter.next();

            @SuppressWarnings("rawtypes")
            Iterator keyIter = keyRing.getPublicKeys();
            while (keyIter.hasNext()) {
                PGPPublicKey key = (PGPPublicKey) keyIter.next();

                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    @Test
    void testExceptionDecryptorIncorrectInputFormatSymmetricEncryptedData() throws Exception {

        byte[] payload = "Not Correct Format".getBytes("UTF-8");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5)
                        .setSecureRandom(new SecureRandom()).setProvider(getProvider()));

        encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator("pw".toCharArray()));

        OutputStream encOut = encGen.open(bos, new byte[1024]);
        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
        OutputStream comOut = new BufferedOutputStream(comData.open(encOut));
        PGPLiteralDataGenerator litData = new PGPLiteralDataGenerator();
        OutputStream litOut = litData.open(comOut, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, new Date(), new byte[1024]);
        litOut.write(payload);
        litOut.flush();
        litOut.close();
        comOut.close();
        encOut.close();
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkeyUnmarshal", bos.toByteArray());
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, IllegalArgumentException.class, null, "The input message body has an invalid format.");
    }

    @Test
    void testExceptionForSignatureVerificationOptionNoSignatureAllowed() throws Exception {

        decryptor.setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_NO_SIGNATURE_ALLOWED);

        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkey", "Test Message");
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, PGPException.class, null,
                "PGP message contains a signature although a signature is not expected");
    }

    @Test
    void testExceptionForSignatureVerificationOptionRequired() throws Exception {

        encryptor.setSignatureKeyUserid(null); // no signature
        decryptor.setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_REQUIRED);

        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        template.sendBody("direct:subkey", "Test Message");
        MockEndpoint.assertIsSatisfied(context);

        checkThrownException(mock, PGPException.class, null,
                "PGP message does not contain any signatures although a signature is expected");
    }

    @Test
    void testSignatureVerificationOptionIgnore() throws Exception {

        // encryptor is sending a PGP message with signature! Decryptor is ignoreing the signature
        decryptor.setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_IGNORE);
        decryptor.setSignatureKeyUserids(null);
        decryptor.setSignatureKeyFileName(null); // no public keyring! --> no signature validation possible

        String payload = "Test Message";
        MockEndpoint mock = getMockEndpoint("mock:unencrypted");
        mock.expectedBodiesReceived(payload);
        template.sendBody("direct:subkey", payload);
        MockEndpoint.assertIsSatisfied(context);

    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() throws Exception {

                onException(Exception.class).handled(true).to("mock:exception");

                // START SNIPPET: pgp-format
                // Public Key FileName
                String keyFileName = getKeyFileName();
                // Private Key FileName
                String keyFileNameSec = getKeyFileNameSec();
                // Keyring Userid Used to Encrypt
                String keyUserid = getKeyUserId();
                // Private key password
                String keyPassword = getKeyPassword();

                from("direct:inline").marshal().pgp(keyFileName, keyUserid).to("mock:encrypted").unmarshal()
                        .pgp(keyFileNameSec, null, keyPassword).to("mock:unencrypted");
                // END SNIPPET: pgp-format

                // START SNIPPET: pgp-format-header
                PGPDataFormat pgpEncrypt = new PGPDataFormat();
                pgpEncrypt.setKeyFileName(keyFileName);
                pgpEncrypt.setKeyUserid(keyUserid);
                pgpEncrypt.setProvider(getProvider());
                pgpEncrypt.setAlgorithm(getAlgorithm());
                pgpEncrypt.setCompressionAlgorithm(getCompressionAlgorithm());

                PGPDataFormat pgpDecrypt = new PGPDataFormat();
                pgpDecrypt.setKeyFileName(keyFileNameSec);
                pgpDecrypt.setPassword(keyPassword);
                pgpDecrypt.setProvider(getProvider());
                pgpDecrypt.setSignatureVerificationOption(
                        PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_NO_SIGNATURE_ALLOWED);

                from("direct:inline2").marshal(pgpEncrypt).to("mock:encrypted").unmarshal(pgpDecrypt).to("mock:unencrypted");

                from("direct:inline-armor").marshal().pgp(keyFileName, keyUserid, null, true, true).to("mock:encrypted")
                        .unmarshal()
                        .pgp(keyFileNameSec, null, keyPassword, true, true).to("mock:unencrypted");
                // END SNIPPET: pgp-format-header

                // START SNIPPET: pgp-format-signature
                PGPDataFormat pgpSignAndEncrypt = new PGPDataFormat();
                pgpSignAndEncrypt.setKeyFileName(keyFileName);
                pgpSignAndEncrypt.setKeyUserid(keyUserid);
                pgpSignAndEncrypt.setSignatureKeyFileName(keyFileNameSec);
                PGPPassphraseAccessor passphraseAccessor = getPassphraseAccessor();
                pgpSignAndEncrypt.setSignatureKeyUserid("Super <sdude@nowhere.net>"); // must be the exact user Id because passphrase is searched in accessor
                pgpSignAndEncrypt.setPassphraseAccessor(passphraseAccessor);
                pgpSignAndEncrypt.setProvider(getProvider());
                pgpSignAndEncrypt.setAlgorithm(getAlgorithm());
                pgpSignAndEncrypt.setHashAlgorithm(getHashAlgorithm());
                pgpSignAndEncrypt.setCompressionAlgorithm(getCompressionAlgorithm());

                PGPDataFormat pgpVerifyAndDecrypt = new PGPDataFormat();
                pgpVerifyAndDecrypt.setKeyFileName(keyFileNameSec);
                pgpVerifyAndDecrypt.setPassword(keyPassword);
                pgpVerifyAndDecrypt.setSignatureKeyFileName(keyFileName);
                pgpVerifyAndDecrypt.setProvider(getProvider());
                pgpVerifyAndDecrypt.setSignatureKeyUserid(keyUserid); // restrict verification to public keys with certain User ID

                from("direct:inline-sign").marshal(pgpSignAndEncrypt).to("mock:encrypted").unmarshal(pgpVerifyAndDecrypt)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature

                // test verifying exception, no public key found corresponding to signature key userIds
                from("direct:verify_exception_sig_userids").marshal(pgpSignAndEncrypt).to("mock:encrypted")
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERIDS)
                        .constant(Arrays.asList(new String[] { "wrong1", "wrong2" }))
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID).constant("wrongUserID")
                        .unmarshal(pgpVerifyAndDecrypt)
                        .to("mock:unencrypted");

                /* ---- key ring as byte array -- */
                // START SNIPPET: pgp-format-key-ring-byte-array
                PGPDataFormat pgpEncryptByteArray = new PGPDataFormat();
                pgpEncryptByteArray.setEncryptionKeyRing(getPublicKeyRing());
                pgpEncryptByteArray.setKeyUserids(getKeyUserIds());
                pgpEncryptByteArray.setProvider(getProvider());
                pgpEncryptByteArray.setAlgorithm(SymmetricKeyAlgorithmTags.DES);
                pgpEncryptByteArray.setCompressionAlgorithm(CompressionAlgorithmTags.UNCOMPRESSED);

                PGPDataFormat pgpDecryptByteArray = new PGPDataFormat();
                pgpDecryptByteArray.setEncryptionKeyRing(getSecKeyRing());
                pgpDecryptByteArray.setPassphraseAccessor(passphraseAccessor);
                pgpDecryptByteArray.setProvider(getProvider());

                from("direct:key-ring-byte-array").streamCaching().marshal(pgpEncryptByteArray).to("mock:encrypted")
                        .unmarshal(pgpDecryptByteArray).to("mock:unencrypted");
                // END SNIPPET: pgp-format-key-ring-byte-array

                // START SNIPPET: pgp-format-signature-key-ring-byte-array
                PGPDataFormat pgpSignAndEncryptByteArray = new PGPDataFormat();
                pgpSignAndEncryptByteArray.setKeyUserid(keyUserid);
                pgpSignAndEncryptByteArray.setSignatureKeyRing(getSecKeyRing());
                pgpSignAndEncryptByteArray.setSignatureKeyUserid(keyUserid);
                pgpSignAndEncryptByteArray.setSignaturePassword(keyPassword);
                pgpSignAndEncryptByteArray.setProvider(getProvider());
                pgpSignAndEncryptByteArray.setAlgorithm(SymmetricKeyAlgorithmTags.BLOWFISH);
                pgpSignAndEncryptByteArray.setHashAlgorithm(HashAlgorithmTags.RIPEMD160);
                pgpSignAndEncryptByteArray.setCompressionAlgorithm(CompressionAlgorithmTags.ZLIB);

                PGPDataFormat pgpVerifyAndDecryptByteArray = new PGPDataFormat();
                pgpVerifyAndDecryptByteArray.setPassphraseAccessor(passphraseAccessor);
                pgpVerifyAndDecryptByteArray.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptByteArray.setProvider(getProvider());
                // restrict verification to public keys with certain User ID
                pgpVerifyAndDecryptByteArray.setSignatureKeyUserids(getSignatureKeyUserIds());
                pgpVerifyAndDecryptByteArray
                        .setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_REQUIRED);

                from("direct:sign-key-ring-byte-array").streamCaching()
                        // encryption key ring can also be set as header
                        .setHeader(PGPDataFormat.ENCRYPTION_KEY_RING).constant(getPublicKeyRing())
                        .marshal(pgpSignAndEncryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.ENCRYPTION_KEY_RING).to("mock:encrypted")
                        // signature key ring can also be set as header
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_RING).constant(getPublicKeyRing())
                        .unmarshal(pgpVerifyAndDecryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.SIGNATURE_KEY_RING).to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature-key-ring-byte-array

                // START SNIPPET: pgp-format-several-signer-keys
                PGPDataFormat pgpSignAndEncryptSeveralSignerKeys = new PGPDataFormat();
                pgpSignAndEncryptSeveralSignerKeys.setKeyUserid(keyUserid);
                pgpSignAndEncryptSeveralSignerKeys.setEncryptionKeyRing(getPublicKeyRing());
                pgpSignAndEncryptSeveralSignerKeys.setSignatureKeyRing(getSecKeyRing());

                List<String> signerUserIds = new ArrayList<>();
                signerUserIds.add("Third (comment third) <email@third.com>");
                signerUserIds.add("Second <email@second.com>");
                pgpSignAndEncryptSeveralSignerKeys.setSignatureKeyUserids(signerUserIds);

                Map<String, String> userId2Passphrase = new HashMap<>();
                userId2Passphrase.put("Third (comment third) <email@third.com>", "sdude");
                userId2Passphrase.put("Second <email@second.com>", "sdude");
                PGPPassphraseAccessor passphraseAccessorSeveralKeys = new DefaultPGPPassphraseAccessor(userId2Passphrase);
                pgpSignAndEncryptSeveralSignerKeys.setPassphraseAccessor(passphraseAccessorSeveralKeys);

                PGPDataFormat pgpVerifyAndDecryptSeveralSignerKeys = new PGPDataFormat();
                pgpVerifyAndDecryptSeveralSignerKeys.setPassphraseAccessor(passphraseAccessor);
                pgpVerifyAndDecryptSeveralSignerKeys.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptSeveralSignerKeys.setSignatureKeyRing(getPublicKeyRing());
                pgpVerifyAndDecryptSeveralSignerKeys.setProvider(getProvider());
                // only specify one expected signature
                List<String> expectedSigUserIds = new ArrayList<>();
                expectedSigUserIds.add("Second <email@second.com>");
                pgpVerifyAndDecryptSeveralSignerKeys.setSignatureKeyUserids(expectedSigUserIds);
                from("direct:several-signer-keys").streamCaching().marshal(pgpSignAndEncryptSeveralSignerKeys)
                        .to("mock:encrypted")
                        .unmarshal(pgpVerifyAndDecryptSeveralSignerKeys).to("mock:unencrypted");
                // END SNIPPET: pgp-format-several-signer-keys

                // test encryption by several key and signing by serveral keys where the keys are specified by one User ID part
                PGPDataFormat pgpSignAndEncryptOneUserIdWithServeralKeys = new PGPDataFormat();
                pgpSignAndEncryptOneUserIdWithServeralKeys.setEncryptionKeyRing(getPublicKeyRing());
                pgpSignAndEncryptOneUserIdWithServeralKeys.setSignatureKeyRing(getSecKeyRing());
                // the two private keys have the same password therefore we do not need a passphrase accessor
                pgpSignAndEncryptOneUserIdWithServeralKeys.setPassword(getKeyPassword());

                PGPDataFormat pgpVerifyAndDecryptOneUserIdWithServeralKeys = new PGPDataFormat();
                pgpVerifyAndDecryptOneUserIdWithServeralKeys.setPassword(getKeyPassword());
                pgpVerifyAndDecryptOneUserIdWithServeralKeys.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptOneUserIdWithServeralKeys.setSignatureKeyRing(getPublicKeyRing());
                pgpVerifyAndDecryptOneUserIdWithServeralKeys.setProvider(getProvider());
                pgpVerifyAndDecryptOneUserIdWithServeralKeys.setSignatureKeyUserids(expectedSigUserIds);
                from("direct:one-userid-several-keys")
                        // there are two keys which have a User ID which contains the string "econd"
                        .setHeader(PGPKeyAccessDataFormat.KEY_USERID)
                        .constant("econd")
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        .constant("econd")
                        .marshal(pgpSignAndEncryptOneUserIdWithServeralKeys)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPKeyAccessDataFormat.KEY_USERID)
                        .removeHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        .to("mock:encrypted")
                        // only specify one expected signature key, to check the first signature
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        .constant("Second <email@second.com>")
                        .unmarshal(pgpVerifyAndDecryptOneUserIdWithServeralKeys)
                        // do it again but now check the second signature key
                        // there are two keys which have a User ID which contains the string "econd"
                        .setHeader(PGPKeyAccessDataFormat.KEY_USERID).constant("econd")
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        .constant("econd").marshal(pgpSignAndEncryptOneUserIdWithServeralKeys)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPKeyAccessDataFormat.KEY_USERID)
                        .removeHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        // only specify one expected signature key, to check the second signature
                        .setHeader(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID)
                        .constant("Third (comment third) <email@third.com>")
                        .unmarshal(pgpVerifyAndDecryptOneUserIdWithServeralKeys).to("mock:unencrypted");

            }

        }, new RouteBuilder() {
            public void configure() {

                onException(Exception.class).handled(true).to("mock:exception");

                from("direct:keyflag").marshal(encryptor).to("mock:encrypted_keyflag");

                // test that the correct subkey is selected during decrypt and verify
                from("direct:subkey").marshal(encryptor).to("mock:encrypted").unmarshal(decryptor).to("mock:unencrypted");

                from("direct:subkeyUnmarshal").unmarshal(decryptor).to("mock:unencrypted");
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {

                PGPPublicKeyAccessor publicKeyAccessor = new DefaultPGPPublicKeyAccessor(getPublicKeyRing());
                //password cannot be set dynamically!
                PGPSecretKeyAccessor secretKeyAccessor
                        = new DefaultPGPSecretKeyAccessor(getSecKeyRing(), "sdude", getProvider());

                PGPKeyAccessDataFormat dfEncryptSignKeyAccess = new PGPKeyAccessDataFormat();
                dfEncryptSignKeyAccess.setPublicKeyAccessor(publicKeyAccessor);
                dfEncryptSignKeyAccess.setSecretKeyAccessor(secretKeyAccessor);
                dfEncryptSignKeyAccess.setKeyUserid(getKeyUserId());
                dfEncryptSignKeyAccess.setSignatureKeyUserid(getKeyUserId());

                PGPKeyAccessDataFormat dfDecryptVerifyKeyAccess = new PGPKeyAccessDataFormat();
                dfDecryptVerifyKeyAccess.setPublicKeyAccessor(publicKeyAccessor);
                dfDecryptVerifyKeyAccess.setSecretKeyAccessor(secretKeyAccessor);
                dfDecryptVerifyKeyAccess.setSignatureKeyUserid(getKeyUserId());

                from("direct:key_access").marshal(dfEncryptSignKeyAccess).to("mock:encrypted")
                        .unmarshal(dfDecryptVerifyKeyAccess)
                        .to("mock:unencrypted");

            }
        }, new RouteBuilder() {
            public void configure() throws Exception {

                // START SNIPPET: pgp-encrypt-sign-without-compressed-data-packet
                PGPDataFormat pgpEncryptSign = new PGPDataFormat();
                pgpEncryptSign.setKeyUserid(getKeyUserId());
                pgpEncryptSign.setSignatureKeyRing(getSecKeyRing());
                pgpEncryptSign.setSignatureKeyUserid(getKeyUserId());
                pgpEncryptSign.setSignaturePassword(getKeyPassword());
                pgpEncryptSign.setProvider(getProvider());
                pgpEncryptSign.setAlgorithm(SymmetricKeyAlgorithmTags.BLOWFISH);
                pgpEncryptSign.setHashAlgorithm(HashAlgorithmTags.RIPEMD160);
                // without compressed data packet
                pgpEncryptSign.setWithCompressedDataPacket(false);

                PGPDataFormat pgpVerifyAndDecryptByteArray = new PGPDataFormat();
                pgpVerifyAndDecryptByteArray.setPassphraseAccessor(getPassphraseAccessor());
                pgpVerifyAndDecryptByteArray.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptByteArray.setProvider(getProvider());
                // restrict verification to public keys with certain User ID
                pgpVerifyAndDecryptByteArray.setSignatureKeyUserids(getSignatureKeyUserIds());
                pgpVerifyAndDecryptByteArray
                        .setSignatureVerificationOption(PGPKeyAccessDataFormat.SIGNATURE_VERIFICATION_OPTION_REQUIRED);

                from("direct:encrypt-sign-without-compressed-data-packet").streamCaching()
                        // encryption key ring can also be set as header
                        .setHeader(PGPDataFormat.ENCRYPTION_KEY_RING).constant(getPublicKeyRing()).marshal(pgpEncryptSign)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.ENCRYPTION_KEY_RING).to("mock:encrypted")
                        // signature key ring can also be set as header
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_RING).constant(getPublicKeyRing())
                        .unmarshal(pgpVerifyAndDecryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.SIGNATURE_KEY_RING).to("mock:unencrypted");
                // END SNIPPET: pgp-encrypt-sign-without-compressed-data-packet
            }
        } };
    }

    public static byte[] getPublicKeyRing() throws Exception {
        return getKeyRing(PUB_KEY_RING_FILE_NAME);
    }

    public static byte[] getSecKeyRing() throws Exception {
        return getKeyRing(SEC_KEY_RING_FILE_NAME);
    }

    private static byte[] getKeyRing(String fileName) throws IOException {
        InputStream is = PGPDataFormatTest.class.getClassLoader().getResourceAsStream(fileName);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, output);
        output.close();
        return output.toByteArray();
    }

    public static PGPPassphraseAccessor getPassphraseAccessor() {
        Map<String, String> userId2Passphrase = Collections.singletonMap("Super <sdude@nowhere.net>", "sdude");
        PGPPassphraseAccessor passphraseAccessor = new DefaultPGPPassphraseAccessor(userId2Passphrase);
        return passphraseAccessor;
    }

    public static void checkThrownException(
            MockEndpoint mock, Class<? extends Exception> cl,
            Class<? extends Exception> expectedCauseClass, String expectedMessagePart)
            throws Exception {
        Exception e = (Exception) mock.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull(e, "Expected excpetion " + cl.getName() + " missing");
        if (e.getClass() != cl) {
            String stackTrace = getStrackTrace(e);
            fail("Exception  " + cl.getName() + " excpected, but was " + e.getClass().getName() + ": " + stackTrace);
        }
        if (expectedMessagePart != null) {
            if (e.getMessage() == null) {
                fail("Expected excption does not contain a message. Stack trace: " + getStrackTrace(e));
            } else {
                if (!e.getMessage().contains(expectedMessagePart)) {
                    fail("Expected excption message does not contain a expected message part " + expectedMessagePart
                         + ".  Stack trace: "
                         + getStrackTrace(e));
                }
            }
        }
        if (expectedCauseClass != null) {
            Throwable cause = e.getCause();
            assertNotNull(cause, "Expected cause exception" + expectedCauseClass.getName() + " missing");
            if (expectedCauseClass != cause.getClass()) {
                fail("Cause exception " + expectedCauseClass.getName() + " expected, but was " + cause.getClass().getName()
                     + ": "
                     + getStrackTrace(e));
            }
        }
    }

    public static String getStrackTrace(Exception e) throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter w = new PrintWriter(os);
        e.printStackTrace(w);
        w.close();
        String stackTrace = new String(os.toByteArray(), "UTF-8");
        return stackTrace;
    }

}
