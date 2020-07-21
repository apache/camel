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
package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.message.BasicHttpResponse;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityParserTest {
    
    public static final String REPORT_CONTENT_TYPE_VALUE =
            "multipart/report; report-type=disposition-notification; boundary=\"----=_Part_56_1672293592.1028122454656\"";

    public static final String REPORT_TYPE_HEADER_VALUE =
            "disposition-notification; boundary=\"----=_Part_56_1672293592.1028122454656\"\r\n";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT =
            "\r\n"
            + "------=_Part_56_1672293592.1028122454656\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n" + "\r\n"
            + "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n" + "\r\n"
            + "------=_Part_56_1672293592.1028122454656\r\n"
            + "Content-Type: message/disposition-notification\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n" + "\r\n"
            + "Reporting-UA: AS2 Server\r\n"
            + "MDN-Gateway: dns; example.com\r\n"
            + "Original-Recipient: rfc822; 0123456780000\r\n"
            + "Final-Recipient: rfc822; 0123456780000\r\n"
            + "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "Disposition: automatic-action/MDN-sent-automatically;\r\n"
            + "  processed/warning: you're awesome\r\n"
            + "Failure: oops-a-failure\r\n" + "Error: oops-an-error\r\n"
            + "Warning: oops-a-warning\r\n"
            + "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String TEXT_PLAIN_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String TEXT_PLAIN_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String TEXT_PLAIN_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n";

    public static final String DISPOSITION_NOTIFICATION_CONTENT =
            "Reporting-UA: AS2 Server\r\n"
            + "MDN-Gateway: dns; example.com\r\n"
            + "Original-Recipient: rfc822; 0123456780000\r\n"
            + "Final-Recipient: rfc822; 0123456780000\r\n"
            + "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "Disposition: automatic-action/MDN-sent-automatically;\r\n"
            + "  processed/warning: you're awesome\r\n"
            + "Failure: oops-a-failure\r\n" + "Error: oops-an-error\r\n"
            + "Warning: oops-a-warning\r\n"
            + "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_REPORTING_UA = "AS2 Server";
    public static final String EXPECTED_MTN_NAME = "example.com";
    public static final String EXPECTED_ORIGINAL_RECIPIENT = "rfc822; 0123456780000";
    public static final String EXPECTED_FINAL_RECIPIENT = "0123456780000";
    public static final String EXPECTED_ORIGINAL_MESSAGE_ID = "<200207310834482A70BF63@\\\"~~foo~~\\\">";
    public static final DispositionMode EXPECTED_DISPOSITION_MODE = DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY;
    public static final String EXPECTED_DISPOSITION_MODIFIER = "warning: you're awesome";
    public static final AS2DispositionType EXPECTED_DISPOSITION_TYPE = AS2DispositionType.PROCESSED;
    public static final String[] EXPECTED_FAILURE = {"oops-a-failure"};
    public static final String[] EXPECTED_ERROR = {"oops-an-error"};
    public static final String[] EXPECTED_WARNING = {"oops-a-warning"};
    public static final String EXPECTED_ENCODED_MESSAGE_DIGEST = "7v7F++fQaNB1sVLFtMRp+dF+eG4=";
    public static final String EXPECTED_DIGEST_ALGORITHM_ID = "sha1";

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    //
    // certificate serial number seed.
    //
    int  serialNo = 1;

    @BeforeEach
    public void setUp() throws Exception {
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void parseMessageDispositionNotificationReportMessageTest() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpStatus.SC_OK, null));
        HttpMessageUtils.setHeaderValue(response, AS2Header.CONTENT_TRANSFER_ENCODING, DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType(REPORT_CONTENT_TYPE_VALUE);
        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_REPORT_CONTENT.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        entity.setContent(is);
        EntityUtils.setMessageEntity(response, entity);

        EntityParser.parseAS2MessageEntity(response);
        HttpEntity parsedEntity = EntityUtils.getMessageEntity(response);
        assertNotNull(parsedEntity, "Unexpected Null message disposition notification report entity");
        assertTrue(parsedEntity instanceof DispositionNotificationMultipartReportEntity, "Unexpected type for message disposition notification report entity");
    }

    @Test
    public void parseMessageDispositionNotificationReportBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_REPORT_CONTENT.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = EntityParser
                .parseMultipartReportEntityBody(inbuffer, DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING);

        assertNotNull(dispositionNotificationMultipartReportEntity, "Unexpected Null disposition notification multipart entity");
        assertEquals(2, dispositionNotificationMultipartReportEntity.getPartCount(), "Unexpected number of body parts");

        assertTrue(dispositionNotificationMultipartReportEntity.getPart(0) instanceof TextPlainEntity, "Unexpected type for first body part");
        assertTrue(dispositionNotificationMultipartReportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity, "Unexpected type for second body part");
    }

    @Test
    public void parseTextPlainBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(TEXT_PLAIN_CONTENT.getBytes(TEXT_PLAIN_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        TextPlainEntity textPlainEntity = EntityParser.parseTextPlainEntityBody(inbuffer, TEXT_PLAIN_CONTENT_BOUNDARY, TEXT_PLAIN_CONTENT_CHARSET_NAME, TEXT_PLAIN_CONTENT_TRANSFER_ENCODING);

        String text = textPlainEntity.getText();

        assertEquals(EXPECTED_TEXT_PLAIN_CONTENT, text, "Unexpected text");
    }

    @Test
    public void parseMessageDispositionNotificationBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_CONTENT.getBytes(DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = EntityParser
                .parseMessageDispositionNotificationEntityBody(inbuffer, DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY,
                        DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME,
                        DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        assertEquals(EXPECTED_REPORTING_UA, messageDispositionNotificationEntity.getReportingUA(), "Unexpected Reporting UA value");
        assertEquals(EXPECTED_MTN_NAME, messageDispositionNotificationEntity.getMtnName(), "Unexpected MTN Name");
        assertEquals(EXPECTED_ORIGINAL_RECIPIENT, messageDispositionNotificationEntity.getExtensionFields().get("Original-Recipient"), "Unexpected Original Recipient");
        assertEquals(EXPECTED_FINAL_RECIPIENT, messageDispositionNotificationEntity.getFinalRecipient(), "Unexpected Final Reciptient");
        assertEquals(EXPECTED_ORIGINAL_MESSAGE_ID, messageDispositionNotificationEntity.getOriginalMessageId(), "Unexpected Original Message ID");
        assertEquals(EXPECTED_DISPOSITION_MODE, messageDispositionNotificationEntity.getDispositionMode(), "Unexpected Disposition Mode");
        assertNotNull(messageDispositionNotificationEntity.getDispositionModifier(), "Unexpected Null Disposition Modifier");
        assertEquals(EXPECTED_DISPOSITION_MODIFIER, messageDispositionNotificationEntity.getDispositionModifier().getModifier(), "Unexpected Disposition Modifier");
        assertEquals(EXPECTED_DISPOSITION_TYPE, messageDispositionNotificationEntity.getDispositionType(), "Unexpected Disposition Type");
        assertArrayEquals(EXPECTED_FAILURE, messageDispositionNotificationEntity.getFailureFields(), "Unexpected Failure Array value");
        assertArrayEquals(EXPECTED_ERROR, messageDispositionNotificationEntity.getErrorFields(), "Unexpected Error Array value");
        assertArrayEquals(EXPECTED_WARNING, messageDispositionNotificationEntity.getWarningFields(), "Unexpected Warning Array value");
        assertNotNull(messageDispositionNotificationEntity.getReceivedContentMic(), "Unexpected Null Received Content MIC");
        assertEquals(EXPECTED_ENCODED_MESSAGE_DIGEST, messageDispositionNotificationEntity.getReceivedContentMic().getEncodedMessageDigest(), "Unexpected Encoded Message Digest");
        assertEquals(EXPECTED_DIGEST_ALGORITHM_ID, messageDispositionNotificationEntity.getReceivedContentMic().getDigestAlgorithmId(), "Unexpected Digest Algorithm ID");
    }

    @Test
    public void parseEnvelopedBodyTest() throws Exception {
        
        Security.addProvider(new BouncyCastleProvider());
        
        //
        // set up our certificates
        //
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        KeyPair issueKP = kpg.generateKeyPair();
        X509Certificate issuerCertificate = makeCertificate(issueKP, issueDN, issueKP, issueDN);

        //
        // certificate we encrypt against
        //
        String encryptDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        KeyPair encryptKP = kpg.generateKeyPair();
        X509Certificate encryptionCertificate = makeCertificate(encryptKP, encryptDN, issueKP, issueDN);

        List<X509Certificate> certList = new ArrayList<>();

        certList.add(encryptionCertificate);
        certList.add(issuerCertificate);
        
        //
        // Create generator
        //
        CMSEnvelopedDataGenerator cmsEnvelopeDataGenerator = new CMSEnvelopedDataGenerator();
        
        JceKeyTransRecipientInfoGenerator recipientInfoGenerator = new JceKeyTransRecipientInfoGenerator(encryptionCertificate);
        cmsEnvelopeDataGenerator.addRecipientInfoGenerator(recipientInfoGenerator);

        //
        // Create encryptor
        //
        OutputEncryptor contentEncryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES128_CCM).build();
        
        //
        // Build Enveloped Entity
        //
        TextPlainEntity textEntity = new TextPlainEntity("This is a super secret messatge!", "US-ASCII", "7bit", false);
        ApplicationPkcs7MimeEnvelopedDataEntity applicationPkcs7MimeEntity = new ApplicationPkcs7MimeEnvelopedDataEntity(textEntity, cmsEnvelopeDataGenerator, contentEncryptor, "binary", true);
        
        MimeEntity decryptedMimeEntity = applicationPkcs7MimeEntity.getEncryptedEntity(encryptKP.getPrivate());
        assertEquals("text/plain; charset=US-ASCII", decryptedMimeEntity.getContentTypeValue(), "Decrypted entity has unexpected content type");
        assertEquals("This is a super secret messatge!", ((TextPlainEntity)decryptedMimeEntity).getText(), "Decrypted entity has unexpected content");
    }

    /**
     * create a basic X509 certificate from the given keys
     */
    private X509Certificate makeCertificate(KeyPair subKP, String subDN, KeyPair issKP, String issDN)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        PublicKey subPub = subKP.getPublic();
        PrivateKey issPriv = issKP.getPrivate();
        PublicKey issPub = issKP.getPublic();

        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(new X500Name(issDN),
                BigInteger.valueOf(serialNo++), new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 100)), new X500Name(subDN), subPub);

        v3CertGen.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(subPub));

        v3CertGen.addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(issPub));

        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(
                v3CertGen.build(new JcaContentSignerBuilder("MD5withRSA").setProvider("BC").build(issPriv)));
    }

    private AuthorityKeyIdentifier createAuthorityKeyId(PublicKey pub) throws IOException {
        SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(pub.getEncoded());

        BcX509ExtensionUtils utils = new BcX509ExtensionUtils();
        return utils.createAuthorityKeyIdentifier(info);
    }

    static SubjectKeyIdentifier createSubjectKeyId(PublicKey pub) throws IOException {
        SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(pub.getEncoded());

        return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
    }
}
