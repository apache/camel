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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
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

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: pgp-format
                // Public Key FileName
                String keyFileName = getKeyFileName();
                // Private Key FileName
                String keyFileNameSec = getKeyFileNameSec();
                // Keyring Userid Used to Encrypt
                String keyUserid = getKeyUserId();
                // Private key password
                String keyPassword = getKeyPassword();

                from("direct:inline")
                        .marshal().pgp(keyFileName, keyUserid)
                        .to("mock:encrypted")
                        .unmarshal().pgp(keyFileNameSec, keyUserid, keyPassword)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format

                // START SNIPPET: pgp-format-header
                PGPDataFormat pgpEncrypt = new PGPDataFormat();
                pgpEncrypt.setKeyFileName(keyFileName);
                pgpEncrypt.setKeyUserid(keyUserid);
                pgpEncrypt.setProvider(getProvider());
                pgpEncrypt.setAlgorithm(getAlgorithm());

                PGPDataFormat pgpDecrypt = new PGPDataFormat();
                pgpDecrypt.setKeyFileName(keyFileNameSec);
                pgpDecrypt.setKeyUserid(keyUserid);
                pgpDecrypt.setPassword(keyPassword);
                pgpDecrypt.setProvider(getProvider());

                from("direct:inline2")
                        .marshal(pgpEncrypt)
                        .to("mock:encrypted")
                        .unmarshal(pgpDecrypt)
                        .to("mock:unencrypted");

                from("direct:inline-armor")
                        .marshal().pgp(keyFileName, keyUserid, null, true, true)
                        .to("mock:encrypted")
                        .unmarshal().pgp(keyFileNameSec, keyUserid, keyPassword, true, true)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format-header

                // START SNIPPET: pgp-format-signature
                PGPDataFormat pgpSignAndEncrypt = new PGPDataFormat();
                pgpSignAndEncrypt.setKeyFileName(keyFileName);
                pgpSignAndEncrypt.setKeyUserid(keyUserid);
                pgpSignAndEncrypt.setSignatureKeyFileName(keyFileNameSec);
                pgpSignAndEncrypt.setSignatureKeyUserid(keyUserid);
                pgpSignAndEncrypt.setSignaturePassword(keyPassword);
                pgpSignAndEncrypt.setProvider(getProvider());
                pgpSignAndEncrypt.setAlgorithm(getAlgorithm());
                pgpSignAndEncrypt.setHashAlgorithm(getHashAlgorithm());
                

                PGPDataFormat pgpVerifyAndDecrypt = new PGPDataFormat();
                pgpVerifyAndDecrypt.setKeyFileName(keyFileNameSec);
                pgpVerifyAndDecrypt.setKeyUserid(keyUserid);
                pgpVerifyAndDecrypt.setPassword(keyPassword);
                pgpVerifyAndDecrypt.setSignatureKeyFileName(keyFileName);
                pgpVerifyAndDecrypt.setSignatureKeyUserid(keyUserid);
                pgpVerifyAndDecrypt.setProvider(getProvider());

                from("direct:inline-sign")
                        .marshal(pgpSignAndEncrypt)
                        .to("mock:encrypted")
                        .unmarshal(pgpVerifyAndDecrypt)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature
                /* ---- key ring as byte array -- */
                // START SNIPPET: pgp-format-key-ring-byte-array
                PGPDataFormat pgpEncryptByteArray = new PGPDataFormat();
                pgpEncryptByteArray.setEncryptionKeyRing(getPublicKeyRing());
                pgpEncryptByteArray.setKeyUserid(keyUserid);
                pgpEncryptByteArray.setProvider(getProvider());
                pgpEncryptByteArray.setAlgorithm(SymmetricKeyAlgorithmTags.DES);

                PGPDataFormat pgpDecryptByteArray = new PGPDataFormat();
                pgpDecryptByteArray.setEncryptionKeyRing(getSecKeyRing());
                pgpDecryptByteArray.setKeyUserid(keyUserid);
                pgpDecryptByteArray.setPassword(keyPassword);
                pgpDecryptByteArray.setProvider(getProvider());

                from("direct:key-ring-byte-array").marshal(pgpEncryptByteArray).to("mock:encrypted").unmarshal(pgpDecryptByteArray)
                        .to("mock:unencrypted");
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

                PGPDataFormat pgpVerifyAndDecryptByteArray = new PGPDataFormat();
                pgpVerifyAndDecryptByteArray.setKeyUserid(keyUserid);
                pgpVerifyAndDecryptByteArray.setPassword(keyPassword);
                pgpVerifyAndDecryptByteArray.setEncryptionKeyRing(getSecKeyRing());
                pgpVerifyAndDecryptByteArray.setSignatureKeyUserid(keyUserid);
                pgpVerifyAndDecryptByteArray.setProvider(getProvider());

                from("direct:sign-key-ring-byte-array")
                // encryption key ring can also be set as header
                        .setHeader(PGPDataFormat.ENCRYPTION_KEY_RING).constant(getPublicKeyRing()).marshal(pgpSignAndEncryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.ENCRYPTION_KEY_RING).to("mock:encrypted")
                        // signature key ring can also be set as header
                        .setHeader(PGPDataFormat.SIGNATURE_KEY_RING).constant(getPublicKeyRing()).unmarshal(pgpVerifyAndDecryptByteArray)
                        // it is recommended to remove the header immediately when it is no longer needed
                        .removeHeader(PGPDataFormat.SIGNATURE_KEY_RING).to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature-key-ring-byte-array
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

}
