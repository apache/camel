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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.CryptoCmsConstants;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsFormatException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsInvalidKeyException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForSignerInfoException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForSignerInfosException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoKeyOrCertificateForAliasException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsSignatureInvalidContentHashException;
import org.apache.camel.component.crypto.cms.sig.DefaultSignedDataVerifierConfiguration;
import org.apache.camel.component.crypto.cms.sig.DefaultSignerInfo;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreator;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreatorConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataVerifier;
import org.apache.camel.component.crypto.cms.sig.SignedDataVerifierFromHeader;
import org.apache.camel.component.crypto.cms.sig.SignerInfo;
import org.apache.camel.component.crypto.cms.util.ExchangeUtil;
import org.apache.camel.component.crypto.cms.util.KeystoreUtil;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SignedDataTest {

    @BeforeClass
    public static void setUpProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testWithCertificatesIncluded() throws Exception {
        signAndVerify("Test Message", "system.jks", "SHA1withRSA", "rsa", true, true);
    }

    @Test
    public void testWithCertificatesIncludedNoSignedAttributes() throws Exception {
        signAndVerify("Test Message", "system.jks", "SHA1withRSA", "rsa", true, true);
    }

    @Test
    public void testWithCertificatesIncludedTimestampSignedAttribute() throws Exception {
        signAndVerify("Test Message", "system.jks", "SHA1withRSA", "rsa", true, true);
    }

    @Test
    public void testWithCertificatesIncludedCertificateSignedAttribute() throws Exception {
        signAndVerify("Test Message", "system.jks", "SHA1withRSA", "rsa", true, true);
    }

    @Test
    public void testWithoutCertificatesIncludedAndDigestAlgorithmSHA1andSignatureAlgorithm() throws Exception {
        signAndVerify("Test Message", "system.jks", "SHA1withDSA", "dsa", true, false);
    }

    private void signAndVerify(String message, String keystoreName, String signatureAlgorithm, String alias, boolean includeContent, boolean includeCertificates)
        throws UnsupportedEncodingException, Exception {

        byte[] signed = sign(message, keystoreName, signatureAlgorithm, includeContent, includeCertificates, alias);
        byte[] result = verify(keystoreName, alias, signed, false);

        Assert.assertEquals(message, new String(result, "UTF-8"));
    }

    private byte[] sign(String message, String keystoreName, String signatureAlgorithm, boolean includeContent, boolean includeCertificates, String... aliases)
        throws UnsupportedEncodingException, Exception {
        KeyStoreParameters keystore = KeystoreUtil.getKeyStoreParameters(keystoreName);

        List<SignerInfo> signers = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            DefaultSignerInfo signerInfo = new DefaultSignerInfo();
            signerInfo.setIncludeCertificates(includeCertificates); // without
                                                                    // certificates,
                                                                    // optional
                                                                    // default
                                                                    // value is
                                                                    // true
            signerInfo.setSignatureAlgorithm(signatureAlgorithm); // mandatory
            signerInfo.setPrivateKeyAlias(alias);
            signerInfo.setKeyStoreParameters(keystore);
            signers.add(signerInfo);
        }

        SignedDataCreatorConfiguration config = new SignedDataCreatorConfiguration(new DefaultCamelContext());
        for (SignerInfo signer : signers) {
            config.addSigner(signer);
        }
        // config.setBlockSize(blockSize); // optional
        config.setIncludeContent(includeContent); // optional default value is
                                                  // true
        config.init();
        SignedDataCreator signer = new SignedDataCreator(config);

        Exchange exchange = ExchangeUtil.getExchange();
        exchange.getIn().setBody(new ByteArrayInputStream(message.getBytes("UTF-8")));
        signer.process(exchange);
        byte[] signed = (byte[])exchange.getMessage().getBody();
        return signed;
    }

    private byte[] verify(String keystoreName, String alias, byte[] signed, boolean base64) throws Exception, UnsupportedEncodingException {
        DefaultSignedDataVerifierConfiguration verifierConf = getCryptoCmsSignedDataVerifierConf(keystoreName, Collections.singletonList(alias), base64);

        SignedDataVerifier verifier = new SignedDataVerifier(verifierConf);

        InputStream is = new BufferedInputStream(new ByteArrayInputStream(signed));
        Exchange exchangeVeri = ExchangeUtil.getExchange();
        exchangeVeri.getIn().setBody(is);
        verifier.process(exchangeVeri);
        byte[] result = (byte[])exchangeVeri.getMessage().getBody();
        return result;
    }

    DefaultSignedDataVerifierConfiguration getCryptoCmsSignedDataVerifierConf(String keystoreName, Collection<String> aliases, boolean base64)
        throws GeneralSecurityException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        KeyStoreParameters keystorePas = KeystoreUtil.getKeyStoreParameters(keystoreName);
        KeyStore keystore = keystorePas.createKeyStore();

        KeyStore verifierKeystore = KeyStore.getInstance("JCEKS");
        verifierKeystore.load(null, "".toCharArray());
        // add only verifier certs
        for (String alias : aliases) {
            Certificate verifierCert = keystore.getCertificate(alias);
            if (verifierCert != null) {
                verifierKeystore.setCertificateEntry(alias, verifierCert);
            }
        }
        DefaultSignedDataVerifierConfiguration verifierConf = new DefaultSignedDataVerifierConfiguration();

        verifierConf.setKeyStore(verifierKeystore);
        verifierConf.setFromBase64(base64);
        return verifierConf;
    }

    @Test
    public void signWithTwoAliases() throws Exception {
        sign("", "system.jks", "SHA1withRSA", true, false, "rsa", "rsa2");
    }

    @Test(expected = CryptoCmsNoKeyOrCertificateForAliasException.class)
    public void signWithTwoAliasesOneWithNoPrivateKeyInKeystore() throws Exception {
        sign("Test Message", "system.jks", "SHA1withDSA", true, false, "dsa", "noEntry");
    }

    @Test(expected = CryptoCmsNoKeyOrCertificateForAliasException.class)
    public void signWrongAlias() throws Exception {
        sign("Test Message", "system.jks", "SHA1withDSA", true, false, "wrong");

    }

    @Test
    public void signEmptyContent() throws Exception {
        sign("", "system.jks", "SHA1withDSA", true, false, "dsa");
    }

    @Test(expected = CryptoCmsInvalidKeyException.class)
    public void signSignatureAlgorithmNotCorrespondingToPrivateKey() throws Exception {
        sign("testMessage", "system.jks", "MD5withRSA", true, false, "dsa");
    }

    @Test(expected = IllegalArgumentException.class)
    public void signWrongSignatureAlgorithm() throws Exception {
        sign("testMessage", "system.jks", "wrongRSA", true, false, "rsa");
    }

    @Test
    public void verifySignedDataWithoutSignedContent() throws Exception {
        InputStream is = SignedDataTest.class.getClassLoader().getResourceAsStream("detached_signature.binary");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copy(is, os);
        byte[] signed = os.toByteArray();
        try {
            verify("system.jks", "rsa", signed, false);
        } catch (CryptoCmsException e) {
            String message = e.getMessage();
            assertEquals("PKCS7/CMS signature validation not possible: The content for which the hash-value must be calculated is missing in the PKCS7/CMS signed data instance. "
                         + "Please check the configuration of the sender of the PKCS7/CMS signature.", message);
            return;
        }
        fail("Exception expected");
    }

    @Test(expected = CryptoCmsNoCertificateForSignerInfosException.class)
    public void verifyNoVerifierCerts() throws Exception {

        byte[] signed = sign("Test Message", "system.jks", "SHA1withRSA", true, true, "rsa");

        verify("system.jks", "wrongAlias", signed, false); // wrongAlias means
                                                           // that no
                                                           // certificates are
                                                           // added to the
                                                           // verifier keystore
    }

    @Test(expected = CryptoCmsFormatException.class)
    public void verifyWrongFormat() throws Exception {

        verify("system.jks", "rsa", "test".getBytes(), false);
    }

    @Test(expected = CryptoCmsFormatException.class)
    public void verifyWrongFormatInHeader() throws Exception {

        verifyContentWithSeparateSignature(new ByteArrayInputStream("ABCDEFG1ABCDEFG1ABCDEFG1".getBytes()), new ByteArrayInputStream("ABCDEFG1ABCDEFG1ABCDEFG1".getBytes()), "rsa");
    }

    @Test
    public void verifyContentWithSeparateSignature() throws Exception {

        InputStream message = new ByteArrayInputStream("Test Message".getBytes(StandardCharsets.UTF_8));

        InputStream signature = this.getClass().getClassLoader().getResourceAsStream("detached_signature.binary");
        assertNotNull(signature);

        verifyContentWithSeparateSignature(message, signature, "rsa");
    }

    @Test(expected = CryptoCmsSignatureInvalidContentHashException.class)
    public void verifyContentWithSeparateSignatureWrongContent() throws Exception {

        InputStream message = new ByteArrayInputStream("Wrong Message".getBytes());

        InputStream signature = this.getClass().getClassLoader().getResourceAsStream("detached_signature.binary");
        assertNotNull(signature);

        verifyContentWithSeparateSignature(message, signature, "rsa");

    }

    private void verifyContentWithSeparateSignature(InputStream content, InputStream signature, String alias) throws Exception {

        DefaultSignedDataVerifierConfiguration verifierConf = getCryptoCmsSignedDataVerifierConf("system.jks", Collections.singletonList(alias), Boolean.FALSE);
        SignedDataVerifier verifier = new SignedDataVerifierFromHeader(verifierConf);

        Exchange exchange = ExchangeUtil.getExchange();
        exchange.getIn().setBody(content);
        exchange.getIn().setHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, signature);
        verifier.process(exchange);
    }

    @Test
    public void verifyWithServeralAliases() throws Exception {
        verifyDetachedSignatureWithKeystore("system.jks", "rsa", "rsa2");
    }

    @Test
    public void verifyWithServeralAliasesOneWithNoEntryInKeystore() throws Exception {
        verifyDetachedSignatureWithKeystore("system.jks", "noEntry", "rsa");
    }

    @Test(expected = CryptoCmsException.class)
    public void verifyWithEmptyAlias() throws Exception {
        verifyDetachedSignatureWithKeystore("system.jks", "");
    }

    @Test(expected = CryptoCmsNoCertificateForSignerInfoException.class)
    public void verifyDetachedSignatureWithAliasNotFittingToSigner() throws Exception {
        verifyDetachedSignatureWithKeystore("system.jks", "rsa2");
    }

    @Test(expected = CryptoCmsNoCertificateForSignerInfosException.class)
    public void verifyDetachedSignatureWithAliasNotFittingToSignerWithVerifiyAllSignaturesFalse() throws Exception {
        verifyDetachedSignatureWithKeystore("system.jks", Boolean.FALSE, "rsa2");
    }

    private void verifyDetachedSignatureWithKeystore(String keystoreName, String... aliases) throws FileNotFoundException, CryptoCmsException, Exception {
        verifyDetachedSignatureWithKeystore(keystoreName, Boolean.TRUE, aliases);
    }

    private void verifyDetachedSignatureWithKeystore(String keystoreName, Boolean verifyAllSignatures, String... aliases)
        throws FileNotFoundException, CryptoCmsException, Exception {

        InputStream message = new ByteArrayInputStream("Test Message".getBytes(StandardCharsets.UTF_8));

        assertNotNull(message);

        DefaultSignedDataVerifierConfiguration verifierConf = getCryptoCmsSignedDataVerifierConf(keystoreName, Arrays.asList(aliases), Boolean.FALSE);
        verifierConf.setVerifySignaturesOfAllSigners(verifyAllSignatures);

        verifierConf.setSignedDataHeaderBase64(Boolean.TRUE);

        SignedDataVerifier verifier = new SignedDataVerifierFromHeader(verifierConf);

        InputStream signature = this.getClass().getClassLoader().getResourceAsStream("detached_signature.base64");
        assertNotNull(signature);

        Exchange exchange = ExchangeUtil.getExchange();
        exchange.getIn().setBody(message);
        exchange.getIn().setHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, signature);
        verifier.process(exchange);
    }

    @Test
    public void signatureAndContentSeparatedExplicitMode() throws Exception {
        String keystoreName = "system.jks";
        String alias = "rsa";
        KeyStoreParameters keystore = KeystoreUtil.getKeyStoreParameters(keystoreName);

        DefaultSignerInfo signerInfo = new DefaultSignerInfo();
        signerInfo.setIncludeCertificates(false); // without certificates,
                                                  // optional default value is
                                                  // true
        signerInfo.setSignatureAlgorithm("SHA1withRSA"); // mandatory
        signerInfo.setPrivateKeyAlias(alias);
        signerInfo.setKeyStoreParameters(keystore);

        SignedDataCreatorConfiguration config = new SignedDataCreatorConfiguration(new DefaultCamelContext());
        config.addSigner(signerInfo);
        config.setIncludeContent(false); // optional default value is true
        config.setToBase64(Boolean.TRUE);
        config.init();
        SignedDataCreator signer = new SignedDataCreator(config);

        String message = "Test Message";

        Exchange exchange = ExchangeUtil.getExchange();

        exchange.getIn().setBody(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
        signer.process(exchange);

        byte[] signature = exchange.getMessage().getHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, byte[].class);

        DefaultSignedDataVerifierConfiguration verifierConf = getCryptoCmsSignedDataVerifierConf(keystoreName, Collections.singleton(alias), Boolean.FALSE);
        verifierConf.setSignedDataHeaderBase64(Boolean.TRUE);

        SignedDataVerifier verifier = new SignedDataVerifierFromHeader(verifierConf);

        exchange = ExchangeUtil.getExchange();
        exchange.getIn().setBody(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
        exchange.getIn().setHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, new ByteArrayInputStream(signature));
        verifier.process(exchange);
    }

    private void signAndVerifyByDSASigAlgorithm(String sigAlgorithm) throws UnsupportedEncodingException, Exception {
        // digest algorithm is calculated
        signAndVerify("Test Message", "system.jks", sigAlgorithm, "dsa", true, false);
    }

    private void signAndVerifyByRSASigAlgorithm(String sigAlgorithm) throws UnsupportedEncodingException, Exception {
        // digest algorithm is calculated
        signAndVerify("Test Message", "system.jks", sigAlgorithm, "rsa", true, false);
    }

    @Test
    public void testSigAlgorithmSHADSA() throws Exception {
        signAndVerifyByDSASigAlgorithm("SHA1withDSA");
    }

    // SHA224withDSA
    @Test
    public void testSigAlgorithmSHA224withDSA() throws Exception {
        signAndVerifyByDSASigAlgorithm("SHA224withDSA");
    }

    // SHA256withDSA
    @Test
    public void testSigAlgorithmSHA256withDSA() throws Exception {
        signAndVerifyByDSASigAlgorithm("SHA256withDSA");
    }

    // SHA1withECDSA // ECSDSA keys not supported
    @Test(expected = CryptoCmsException.class)
    public void testSigAlgorithmSHA1withECDSA() throws Exception {
        signAndVerifyByDSASigAlgorithm("SHA1withECDSA");
    }

    // MD2withRSA
    @Test
    public void testSigAlgorithmMD2withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("MD2withRSA");
    }

    // MD5/RSA
    // MD2withRSA
    @Test
    public void testSigAlgorithmMD5withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("MD5withRSA");
    }

    // SHA/RSA
    @Test
    public void testSigAlgorithmSHAwithRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA1withRSA"); // SHA/RSA");
    }

    // SHA224/RSA
    @Test
    public void testSigAlgorithmSHA224withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA224withRSA");
    }

    // SHA256/RSA
    @Test
    public void testSigAlgorithmSHA256withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA256withRSA");
    }

    // SHA384/RSA
    @Test
    public void testSigAlgorithmSHA384withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA384withRSA");
    }

    // SHA512/RSA
    @Test
    public void testSigAlgorithmSHA512withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA512withRSA");
    }

    // RIPEMD160/RSA
    @Test
    public void testSigAlgorithmRIPEMD160withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("RIPEMD160withRSA");
    }

    // RIPEMD128/RSA
    @Test
    public void testSigAlgorithmRIPEMD128withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("RIPEMD128withRSA");
    }

    // RIPEMD256/RSA
    @Test
    public void testSigAlgorithmRIPEMD256withRSA() throws Exception {
        signAndVerifyByRSASigAlgorithm("RIPEMD256withRSA");
    }

    @Test(expected = CryptoCmsInvalidKeyException.class)
    public void testSigAlgorithmDoesnotFitToDSAPrivateKey() throws Exception {
        signAndVerifyByDSASigAlgorithm("RIPEMD128withRSA");
    }

    @Test(expected = CryptoCmsInvalidKeyException.class)
    public void testSigAlgorithmDoesnotFitToRSAPrivateKey() throws Exception {
        signAndVerifyByRSASigAlgorithm("SHA224withDSA");
    }
}
