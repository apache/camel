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
package org.apache.camel.component.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.crypt.DefaultEnvelopedDataDecryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.DefaultKeyTransRecipientInfo;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataDecryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.RecipientInfo;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsFormatException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForRecipientsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoKeyOrCertificateForAliasException;
import org.apache.camel.component.crypto.cms.util.ExchangeUtil;
import org.apache.camel.component.crypto.cms.util.KeystoreUtil;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class EnvelopedDataTest {

    @BeforeClass
    public static void setUpProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void executeDESedeCBClength192() throws Exception {
        encryptDecrypt("system.jks", "rsa", "DESede/CBC/PKCS5Padding", 192);
    }

    @Test
    public void executeDESedeCBClength128() throws Exception {
        encryptDecrypt("system.jks", "rsa", "DESede/CBC/PKCS5Padding", 128);
    }

    @Test
    public void executeDESCBCkeyLength64() throws Exception {
        encryptDecrypt("system.jks", "rsa", "DES/CBC/PKCS5Padding", 64);
    }

    @Test
    public void executeDESCBCkeyLength56() throws Exception {
        encryptDecrypt("system.jks", "rsa", "DES/CBC/PKCS5Padding", 56);
    }

    @Test
    public void executeCAST5CBCkeyLength128() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 128);
    }

    @Test
    public void executeCAST5CBCkeyLength120() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 120);
    }

    @Test
    public void executeCAST5CBCkeyLength112() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 112);
    }

    @Test
    public void executeCAST5CBCkeyLength104() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 104);
    }

    @Test
    public void executeCAST5CBCkeyLength96() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 96);
    }

    @Test
    public void executeCAST5CBCkeyLength88() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 88);
    }

    @Test
    public void executeCAST5CBCkeyLength80() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 80);
    }

    @Test
    public void executeCAST5CBCkeyLength72() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 72);
    }

    @Test
    public void executeCAST5CBCkeyLength64() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 64);
    }

    @Test
    public void executeCAST5CBCkeyLength56() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 56);
    }

    @Test
    public void executeCAST5CBCkeyLength48() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 48);
    }

    @Test
    public void executeCAST5CBCkeyLength40() throws Exception {
        encryptDecrypt("system.jks", "rsa", "CAST5/CBC/PKCS5Padding", 40);
    }

    @Test
    public void executeRC2CBCkeyLength128() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 128);
    }

    @Test
    public void executeRC2CBCkeyLength120() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 120);
    }

    @Test
    public void executeRC2CBCkeyLength112() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 112);
    }

    @Test
    public void executeRC2CBCkeyLength104() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 104);
    }

    @Test
    public void executeRC2CBCkeyLength96() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 96);
    }

    @Test
    public void executeRC2CBCkeyLength88() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 88);
    }

    @Test
    public void executeRC2CBCkeyLength80() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 80);
    }

    @Test
    public void executeRC2CBCkeyLength72() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 72);
    }

    @Test
    public void executeRC2CBCkeyLength64() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 64);
    }

    @Test
    public void executeRC2CBCkeyLength56() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 56);
    }

    @Test
    public void executeRC2CBCkeyLength48() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 48);
    }

    @Test
    public void executeRC2CBCkeyLength40() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC2/CBC/PKCS5Padding", 40);
    }

    @Test
    public void executeCamelliaCBCKeySize128() throws Exception {
        encryptDecrypt("system.jks", "rsa", "Camellia/CBC/PKCS5Padding", 128);
    }

    /** Works if strong encryption policy jars are installed. */
    @Ignore
    @Test(expected = CryptoCmsException.class)
    public void executeCamelliaCBCKeySize256() throws Exception {
        encryptDecrypt("system.jks", "rsa", "Camellia/CBC/PKCS5Padding", 256);
    }

    /** Works if strong encryption policy jars are installed. */
    @Ignore
    @Test(expected = CryptoCmsException.class)
    public void executeCamelliaCBCKeySize192() throws Exception {
        encryptDecrypt("system.jks", "rsa", "Camellia/CBC/PKCS5Padding", 192);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNoInWhiteListCamellia256CBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "Camellia256/CBC/PKCS5Padding", 256);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListCamellia192CBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "Camellia192/CBC/PKCS5Padding", 192);
    }

    /** Works if strong encryption policy jars are installed. */
    @Ignore
    @Test(expected = CryptoCmsException.class)
    public void executeAESCBCKeySize256() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES/CBC/PKCS5Padding", 256);
    }

    /** Works if strong encryption policy jars are installed. */
    @Ignore
    @Test(expected = CryptoCmsException.class)
    public void executeAESCBCKeySize192() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES/CBC/PKCS5Padding", 192);
    }

    @Test
    public void executeAESCBCKeySize128() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES/CBC/PKCS5Padding", 128);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES256CBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES256/CBC/PKCS5Padding", 256);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES192CBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES192/CBC/PKCS5Padding", 192);
    }

    @Test(expected = CryptoCmsException.class)
    public void executerNoImplRSAECB() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RSA/ECB/OAEP", 0);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAESGCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES/GCM/NoPadding", 128);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES192GCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES192/GCM/NoPadding", 192);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES256GCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES256/GCM/NoPadding", 256);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES256CCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES256/CCM/NoPadding", 256);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListIDEACBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "IDEA/CBC/PKCS5Padding", 128);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAESCCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES/CCM/NoPadding", 128);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListAES192CCM() throws Exception {
        encryptDecrypt("system.jks", "rsa", "AES192/CCM/NoPadding", 192);
    }

    @Test(expected = CryptoCmsException.class)
    public void executeNotInWhiteListRC5CBC() throws Exception {
        encryptDecrypt("system.jks", "rsa", "RC5/CBC/PKCS5Padding", 0);
    }

    @Test(expected = CryptoCmsException.class)
    public void wrongSecretKeyLength() throws Exception {
        encrypt("system.jks", "DESede/CBC/PKCS5Padding", 200, "testMessage", "rsa");
    }

    @Test(expected = CryptoCmsException.class)
    public void wrongContentEncryptionAlgorithm() throws Exception {
        encryptDecrypt("system.jks", "rsa", "WrongDESede/CBC/PKCS5Padding", 200);
    }

    @Test(expected = CryptoCmsNoKeyOrCertificateForAliasException.class)
    public void wrongEncryptAlias() throws Exception {
        encrypt("system.jks", "DESede/CBC/PKCS5Padding", 128, "testMessage", "wrongAlias");
    }

    @Test(expected = CryptoCmsNoKeyOrCertificateForAliasException.class)
    public void encryptWrongAliasAndCorrectAlias() throws Exception {
        encrypt("system.jks", "DESede/CBC/PKCS5Padding", 128, "testMessage", "wrongAlias", "rsa");
    }

    @Test(expected = CryptoCmsNoKeyOrCertificateForAliasException.class)
    public void encryptTwoWrongAliases() throws Exception {
        encrypt("system.jks", "DESede/CBC/PKCS5Padding", 128, "testMessage", "wrongAlias", "wrongAlias2");
    }

    @Test
    public void encryptTwoCorrectAliases() throws Exception {
        encrypt("system.jks", "DESede/CBC/PKCS5Padding", 128, "testMessage", "rsa2", "rsa");
    }

    @Test(expected = CryptoCmsFormatException.class)
    public void wrongEncryptedMessage() throws Exception {
        decrypt("system.jks", "TestMessage".getBytes());
    }

    @Test(expected = CryptoCmsFormatException.class)
    public void wrongEncryptedEmptyMessage() throws Exception {
        decrypt("system.jks", new byte[0]);
    }

    @Test
    public void decryptionWithEmptyAlias() throws Exception {

        byte[] bytes = null;
        try {
            bytes = encrypt("system.jks", "DESede/CBC/PKCS5Padding", 192, "Test Message", "rsa");
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        decrypt("system.jks", bytes);
    }

    @Test(expected = CryptoCmsNoCertificateForRecipientsException.class)
    public void decryptionWithNullAliasWrongKeystore() throws Exception {

        byte[] bytes = null;
        try {
            bytes = encrypt("system.jks", "DESede/CBC/PKCS5Padding", 192, "Test Message", "rsa");
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        decrypt("test.jks", bytes);
    }

    // @Test
    // public void invalidContentTypeEnvelopedData() throws Exception {
    // try {
    // encryptDecrypt("system.jks", "rsa", "DESede/CBC/PKCS5Padding", 192,
    // CmsEnvelopedDataDecryptorConfiguration.SIGNEDANDENVELOPEDDATA);
    // } catch (CmsException e) {
    // Assert.assertTrue(e.getMessage().contains("The PKCS#7/CMS decryptor step
    // does not accept PKCS#7/CMS messages of content type 'Enveloped Data'"));
    // return;
    // }
    // Assert.fail("Exception expected");
    // }

    private void encryptDecrypt(String keystoreName, String alias, String contentEncryptionAlgorithm, int secretKeyLength) throws Exception {
        String message = "Test Message";

        byte[] encrypted = encrypt(keystoreName, contentEncryptionAlgorithm, secretKeyLength, message, alias);
        byte[] decrypted = decrypt(keystoreName, encrypted);

        String actual = new String(decrypted, "UTF-8");
        Assert.assertEquals(message, actual);
    }

    private byte[] encrypt(String keystoreName, String contentEncryptionAlgorithm, int secretKeyLength, String message, String... aliases)
        throws UnsupportedEncodingException, Exception {
        KeyStoreParameters keystorePas = KeystoreUtil.getKeyStoreParameters(keystoreName);

        List<RecipientInfo> recipients = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            DefaultKeyTransRecipientInfo recipient = new DefaultKeyTransRecipientInfo();
            recipient.setCertificateAlias(alias);
            recipient.setKeyStoreParameters(keystorePas);
            recipients.add(recipient);
        }

        EnvelopedDataEncryptorConfiguration enConf = new EnvelopedDataEncryptorConfiguration(null);
        enConf.setContentEncryptionAlgorithm(contentEncryptionAlgorithm);
        for (RecipientInfo recipient : recipients) {
            enConf.setRecipient(recipient);
        }
        enConf.setSecretKeyLength(secretKeyLength); // optional
        // enConf.setBlockSize(2048); // optional
        enConf.init();
        EnvelopedDataEncryptor encryptor = new EnvelopedDataEncryptor(enConf);

        Exchange exchange = ExchangeUtil.getExchange();
        exchange.getIn().setBody(new ByteArrayInputStream(message.getBytes("UTF-8")));
        encryptor.process(exchange);
        byte[] encrypted = (byte[])exchange.getMessage().getBody();
        return encrypted;
    }

    private byte[] decrypt(String keystoreName, byte[] encrypted) throws UnsupportedEncodingException, Exception, IOException {

        KeyStoreParameters keystore = KeystoreUtil.getKeyStoreParameters(keystoreName);

        Exchange exchangeDecrypt = ExchangeUtil.getExchange();
        exchangeDecrypt.getIn().setBody(new ByteArrayInputStream(encrypted));

        DefaultEnvelopedDataDecryptorConfiguration conf = new DefaultEnvelopedDataDecryptorConfiguration();
        conf.setKeyStoreParameters(keystore);
        EnvelopedDataDecryptor decryptor = new EnvelopedDataDecryptor(conf);

        decryptor.process(exchangeDecrypt);

        byte[] decrypted = (byte[])exchangeDecrypt.getMessage().getBody();

        return decrypted;
    }

}
