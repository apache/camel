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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code pgp} data format encrypts with AES-256 and signs with SHA-256 unless other algorithms are configured.
 */
class PGPDataFormatDefaultAlgorithmsTest {

    private static final String PUB_KEY_RING = "org/apache/camel/component/crypto/pubring.gpg";
    private static final String SEC_KEY_RING = "org/apache/camel/component/crypto/secring.gpg";
    private static final String PASSPHRASE = "sdude";
    private static final String PAYLOAD = "Hello PGP";

    @BeforeAll
    static void installProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void theDefaultsAreAes256AndSha256() {
        PGPDataFormat dataFormat = new PGPDataFormat();

        assertEquals(SymmetricKeyAlgorithmTags.AES_256, dataFormat.getAlgorithm());
        assertEquals(HashAlgorithmTags.SHA256, dataFormat.getHashAlgorithm());
    }

    @Test
    void aSignedMessageIsEncryptedWithAes256AndSignedWithSha256() throws Exception {
        PGPDataFormat encryptor = new PGPDataFormat();
        encryptor.setKeyFileName(PUB_KEY_RING);
        encryptor.setKeyUserid("sdude");
        encryptor.setSignatureKeyFileName(SEC_KEY_RING);
        encryptor.setSignatureKeyUserid("sdude");
        encryptor.setSignaturePassword(PASSPHRASE);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            encryptor.start();

            Exchange exchange = new DefaultExchange(context);
            ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
            encryptor.marshal(exchange, PAYLOAD.getBytes(StandardCharsets.UTF_8), encrypted);

            PGPObjectFactory factory = new PGPObjectFactory(
                    PGPUtil.getDecoderStream(new ByteArrayInputStream(encrypted.toByteArray())),
                    new BcKeyFingerprintCalculator());
            PGPEncryptedDataList encryptedDataList = (PGPEncryptedDataList) factory.nextObject();
            PGPPublicKeyEncryptedData encryptedData = (PGPPublicKeyEncryptedData) encryptedDataList.get(0);
            PGPPrivateKey privateKey = PGPDataFormatUtil.findPrivateKeyWithKeyId(context, SEC_KEY_RING, null,
                    encryptedData.getKeyID(), PASSPHRASE, null, "BC");
            PublicKeyDataDecryptorFactory decryptorFactory
                    = new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(privateKey);

            assertEquals(SymmetricKeyAlgorithmTags.AES_256, encryptedData.getSymmetricAlgorithm(decryptorFactory));

            try (InputStream clear = encryptedData.getDataStream(decryptorFactory)) {
                PGPObjectFactory clearFactory = new PGPObjectFactory(clear, new BcKeyFingerprintCalculator());
                PGPCompressedData compressed = (PGPCompressedData) clearFactory.nextObject();
                PGPObjectFactory signedFactory
                        = new PGPObjectFactory(compressed.getDataStream(), new BcKeyFingerprintCalculator());
                PGPOnePassSignatureList signatures = (PGPOnePassSignatureList) signedFactory.nextObject();

                assertEquals(HashAlgorithmTags.SHA256, signatures.get(0).getHashAlgorithm());
            }
        }
    }
}
