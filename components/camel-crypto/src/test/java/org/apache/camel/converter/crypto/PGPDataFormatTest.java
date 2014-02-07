/**
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.junit.Test;

public class PGPDataFormatTest extends AbstractPGPDataFormatTest {

    private static final String SEC_KEY_RING_FILE_NAME = "org/apache/camel/component/crypto/secring.gpg";
    private static final String PUB_KEY_RING_FILE_NAME = "org/apache/camel/component/crypto/pubring.gpg";

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
        List<String> userids = new ArrayList<String>(2);
        userids.add("second");
        userids.add(getKeyUserId());
        return userids;
    }

    protected List<String> getSignatureKeyUserIds() {
        List<String> userids = new ArrayList<String>(2);
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
    public void testEncryption() throws Exception {
        doRoundTripEncryptionTests("direct:inline");
    }

    @Test
    public void testEncryption2() throws Exception {
        doRoundTripEncryptionTests("direct:inline2");
    }

    @Test
    public void testEncryptionArmor() throws Exception {
        doRoundTripEncryptionTests("direct:inline-armor");
    }

    @Test
    public void testEncryptionSigned() throws Exception {
        doRoundTripEncryptionTests("direct:inline-sign");
    }

    @Test
    public void testEncryptionKeyRingByteArray() throws Exception {
        doRoundTripEncryptionTests("direct:key-ring-byte-array");
    }

    @Test
    public void testEncryptionSignedKeyRingByteArray() throws Exception {
        doRoundTripEncryptionTests("direct:sign-key-ring-byte-array");
    }

    @Test
    public void testSeveralSignerKeys() throws Exception {
        doRoundTripEncryptionTests("direct:several-signer-keys");
    }
    
    @Test
    public void testOneUserIdWithServeralKeys() throws Exception {
        doRoundTripEncryptionTests("direct:one-userid-several-keys");
    }

    @Test
    public void testVerifyExceptionNoPublicKeyFoundCorrespondingToSignatureUserIds() throws Exception {
        setupExpectations(context, 1, "mock:encrypted");
        MockEndpoint exception = setupExpectations(context, 1, "mock:exception");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        Map<String, Object> headers = getHeaders();
        template.sendBodyAndHeaders("direct:verify_exception_sig_userids", payload, headers);
        assertMockEndpointsSatisfied();

        //check exception text
        Exception e = (Exception) exception.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Expected excpetion  missing", e);
        assertTrue(e.getMessage().contains("No public key found fitting to the signature key Id"));

    }
    

    @Test
    public void testVerifyExceptionNoPassphraseSpecifiedForSignatureKeyUserId() throws Exception {
        MockEndpoint exception = setupExpectations(context, 1, "mock:exception");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        Map<String, Object> headers = new HashMap<String, Object>();
        // add signature user id which does not have a passphrase
        headers.put(PGPDataFormat.SIGNATURE_KEY_USERID, "userIDWithNoPassphrase");
        // the following entry is necessary for the dynamic test
        headers.put(PGPDataFormat.KEY_USERID, "second");
        template.sendBodyAndHeaders("direct:several-signer-keys", payload, headers);
        assertMockEndpointsSatisfied();

        //check exception text
        Exception e = (Exception) exception.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Expected excpetion  missing", e);
        assertTrue(e.getMessage().contains("No passphrase specified for signature key user ID"));

    }
    
 

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {

                onException(IllegalArgumentException.class).handled(true).to("mock:exception");

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

                from("direct:inline2").marshal(pgpEncrypt).to("mock:encrypted").unmarshal(pgpDecrypt).to("mock:unencrypted");

                from("direct:inline-armor").marshal().pgp(keyFileName, keyUserid, null, true, true).to("mock:encrypted").unmarshal()
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
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_USERIDS).constant(Arrays.asList(new String[] {"wrong1", "wrong2"}))
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_USERID).constant("wrongUserID").unmarshal(pgpVerifyAndDecrypt)
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

                from("direct:sign-key-ring-byte-array").streamCaching()
                // encryption key ring can also be set as header
                        .setHeader(PGPDataFormat.ENCRYPTION_KEY_RING).constant(getPublicKeyRing()).marshal(pgpSignAndEncryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.ENCRYPTION_KEY_RING).to("mock:encrypted")
                        // signature key ring can also be set as header
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_RING).constant(getPublicKeyRing()).unmarshal(pgpVerifyAndDecryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.SIGNATURE_KEY_RING).to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature-key-ring-byte-array

                // START SNIPPET: pgp-format-several-signer-keys
                PGPDataFormat pgpSignAndEncryptSeveralSignerKeys = new PGPDataFormat();
                pgpSignAndEncryptSeveralSignerKeys.setKeyUserid(keyUserid);
                pgpSignAndEncryptSeveralSignerKeys.setEncryptionKeyRing(getPublicKeyRing());
                pgpSignAndEncryptSeveralSignerKeys.setSignatureKeyRing(getSecKeyRing());

                List<String> signerUserIds = new ArrayList<String>();
                signerUserIds.add("Third (comment third) <email@third.com>");
                signerUserIds.add("Second <email@second.com>");
                pgpSignAndEncryptSeveralSignerKeys.setSignatureKeyUserids(signerUserIds);

                Map<String, String> userId2Passphrase = new HashMap<String, String>();
                userId2Passphrase.put("Third (comment third) <email@third.com>", "sdude");
                userId2Passphrase.put("Second <email@second.com>", "sdude");
                PGPPassphraseAccessor passphraseAccessorSeveralKeys = new PGPPassphraseAccessorDefault(userId2Passphrase);
                pgpSignAndEncryptSeveralSignerKeys.setPassphraseAccessor(passphraseAccessorSeveralKeys);

                PGPDataFormat pgpVerifyAndDecryptSeveralSignerKeys = new PGPDataFormat();
                pgpVerifyAndDecryptSeveralSignerKeys.setPassphraseAccessor(passphraseAccessor);
                pgpVerifyAndDecryptSeveralSignerKeys.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptSeveralSignerKeys.setSignatureKeyRing(getPublicKeyRing());
                pgpVerifyAndDecryptSeveralSignerKeys.setProvider(getProvider());
                // only specify one expected signature
                List<String> expectedSigUserIds = new ArrayList<String>();
                expectedSigUserIds.add("Second <email@second.com>");
                pgpVerifyAndDecryptSeveralSignerKeys.setSignatureKeyUserids(expectedSigUserIds);
                from("direct:several-signer-keys").streamCaching().marshal(pgpSignAndEncryptSeveralSignerKeys).to("mock:encrypted")
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
                        .setHeader(PGPDataFormat.KEY_USERID)
                        .constant("econd")
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_USERID)
                        .constant("econd")
                        .marshal(pgpSignAndEncryptOneUserIdWithServeralKeys)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.KEY_USERID)
                        .removeHeader(PGPDataFormat.SIGNATURE_KEY_USERID)
                        .to("mock:encrypted")
                        // only specify one expected signature key, to check the first signature
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_USERID)
                        .constant("Second <email@second.com>")
                        .unmarshal(pgpVerifyAndDecryptOneUserIdWithServeralKeys)
                        // do it again but now check the second signature key
                        // there are two keys which have a User ID which contains the string "econd"
                        .setHeader(PGPDataFormat.KEY_USERID).constant("econd").setHeader(PGPDataFormat.SIGNATURE_KEY_USERID)
                        .constant("econd").marshal(pgpSignAndEncryptOneUserIdWithServeralKeys)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.KEY_USERID).removeHeader(PGPDataFormat.SIGNATURE_KEY_USERID)
                        // only specify one expected signature key, to check the second signature
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_USERID).constant("Third (comment third) <email@third.com>")
                        .unmarshal(pgpVerifyAndDecryptOneUserIdWithServeralKeys).to("mock:unencrypted");
            }

        };
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
        PGPPassphraseAccessor passphraseAccessor = new PGPPassphraseAccessorDefault(userId2Passphrase);
        return passphraseAccessor;
    }

}
