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

import static org.apache.camel.component.as2.api.entity.EntityParserTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class EntityParserContentLineEndingTest {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    //
    // certificate serial number seed.
    //
    int serialNo = 1;

    @BeforeEach
    public void setUp() throws Exception {}

    @AfterEach
    public void tearDown() throws Exception {}

    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void parseMessageDispositionNotificationReportMessageTest(String carriageReturn) throws Exception {
        HttpResponse response = new BasicClassicHttpResponse(
                HttpStatus.SC_OK, EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpStatus.SC_OK, null));
        response.setVersion(new ProtocolVersion("HTTP", 1, 1));
        HttpMessageUtils.setHeaderValue(
                response, AS2Header.CONTENT_TRANSFER_ENCODING, DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        InputStream is = new ByteArrayInputStream(
                EntityParserContentProvider.dispositionNotificationReportContent(carriageReturn)
                        .getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        BasicHttpEntity entity = new BasicHttpEntity(is, ContentType.parse(REPORT_CONTENT_TYPE_VALUE));
        EntityUtils.setMessageEntity(response, entity);

        EntityParser.parseAS2MessageEntity(response);
        HttpEntity parsedEntity = EntityUtils.getMessageEntity(response);
        assertNotNull(parsedEntity, "Unexpected Null message disposition notification report entity");
        assertTrue(
                parsedEntity instanceof DispositionNotificationMultipartReportEntity,
                "Unexpected type for message disposition notification report entity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void parseMessageDispositionNotificationReportBodyTest(String carriageReturn) throws Exception {

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = createMdnEntity(
                EntityParserContentProvider.dispositionNotificationReportContent(carriageReturn),
                DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY);

        assertNotNull(
                dispositionNotificationMultipartReportEntity,
                "Unexpected Null disposition notification multipart entity");
        assertEquals(2, dispositionNotificationMultipartReportEntity.getPartCount(), "Unexpected number of body parts");

        assertTrue(
                dispositionNotificationMultipartReportEntity.getPart(0) instanceof TextPlainEntity,
                "Unexpected type for first body part");
        assertTrue(
                dispositionNotificationMultipartReportEntity.getPart(1)
                        instanceof AS2MessageDispositionNotificationEntity,
                "Unexpected type for second body part");
    }

    // verify that parsing the Disposition Notification Report has made no alteration to the entity's body part fields
    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void messageDispositionNotificationReportBodyContentTest(String carriageReturn) throws Exception {

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = createMdnEntity(
                EntityParserContentProvider.dispositionNotificationReportContentUnfolded(carriageReturn),
                DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY);

        String expectedContent = String.format(
                "%s\r\n%s\r\n%s",
                new BasicHeader(AS2Header.CONTENT_TYPE, REPORT_CONTENT_TYPE_VALUE),
                new BasicHeader(
                        AS2Header.CONTENT_TRANSFER_ENCODING, DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING),
                EntityParserContentProvider.dispositionNotificationReportContentUnfolded(carriageReturn));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dispositionNotificationMultipartReportEntity.writeTo(out);

        assertEquals(expectedContent, out.toString(DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void parseTextPlainBodyTest(String carriageReturn) throws Exception {

        InputStream is = new ByteArrayInputStream(
                EntityParserContentProvider.textPlainContent(carriageReturn).getBytes(TEXT_PLAIN_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer =
                new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);

        TextPlainEntity textPlainEntity = EntityParser.parseTextPlainEntityBody(
                inbuffer,
                is,
                TEXT_PLAIN_CONTENT_BOUNDARY,
                TEXT_PLAIN_CONTENT_CHARSET_NAME,
                TEXT_PLAIN_CONTENT_TRANSFER_ENCODING);

        String text = textPlainEntity.getText();

        assertEquals(EntityParserContentProvider.expectedTextPlainContent(carriageReturn), text, "Unexpected text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void parseTextPlainBodyTestWithEntityMarshalling(String carriageReturn) throws Exception {

        InputStream is = new ByteArrayInputStream(
                EntityParserContentProvider.textPlainContent(carriageReturn).getBytes(TEXT_PLAIN_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer =
                new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);

        TextPlainEntity textPlainEntity = EntityParser.parseTextPlainEntityBody(
                inbuffer,
                is,
                TEXT_PLAIN_CONTENT_BOUNDARY,
                TEXT_PLAIN_CONTENT_CHARSET_NAME,
                TEXT_PLAIN_CONTENT_TRANSFER_ENCODING);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        textPlainEntity.setMainBody(true);
        textPlainEntity.writeTo(out);
        String text = out.toString();

        assertEquals(EntityParserContentProvider.expectedTextPlainContent(carriageReturn), text, "Unexpected text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", ""})
    public void parseMessageDispositionNotificationBodyTest(String carriageReturn) throws Exception {

        InputStream is =
                new ByteArrayInputStream(EntityParserContentProvider.dispositionNotificationContent(carriageReturn)
                        .getBytes(DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer =
                new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);

        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity =
                EntityParser.parseMessageDispositionNotificationEntityBody(
                        inbuffer,
                        is,
                        DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY,
                        DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME);

        assertEquals(
                EXPECTED_REPORTING_UA,
                messageDispositionNotificationEntity.getReportingUA(),
                "Unexpected Reporting UA value");
        assertEquals(EXPECTED_MTN_NAME, messageDispositionNotificationEntity.getMtnName(), "Unexpected MTN Name");
        assertEquals(
                EXPECTED_ORIGINAL_RECIPIENT,
                messageDispositionNotificationEntity.getExtensionFields().get("Original-Recipient"),
                "Unexpected Original Recipient");
        assertEquals(
                EXPECTED_FINAL_RECIPIENT,
                messageDispositionNotificationEntity.getFinalRecipient(),
                "Unexpected Final Reciptient");
        assertEquals(
                EXPECTED_ORIGINAL_MESSAGE_ID,
                messageDispositionNotificationEntity.getOriginalMessageId(),
                "Unexpected Original Message ID");
        assertEquals(
                EXPECTED_DISPOSITION_MODE,
                messageDispositionNotificationEntity.getDispositionMode(),
                "Unexpected Disposition Mode");
        assertNotNull(
                messageDispositionNotificationEntity.getDispositionModifier(), "Unexpected Null Disposition Modifier");
        assertEquals(
                EXPECTED_DISPOSITION_MODIFIER,
                messageDispositionNotificationEntity.getDispositionModifier().getModifier(),
                "Unexpected Disposition Modifier");
        assertEquals(
                EXPECTED_DISPOSITION_TYPE,
                messageDispositionNotificationEntity.getDispositionType(),
                "Unexpected Disposition Type");
        assertArrayEquals(
                EXPECTED_FAILURE,
                messageDispositionNotificationEntity.getFailureFields(),
                "Unexpected Failure Array value");
        assertArrayEquals(
                EXPECTED_ERROR, messageDispositionNotificationEntity.getErrorFields(), "Unexpected Error Array value");
        assertArrayEquals(
                EXPECTED_WARNING,
                messageDispositionNotificationEntity.getWarningFields(),
                "Unexpected Warning Array value");
        assertNotNull(
                messageDispositionNotificationEntity.getReceivedContentMic(), "Unexpected Null Received Content MIC");
        assertEquals(
                EXPECTED_ENCODED_MESSAGE_DIGEST,
                messageDispositionNotificationEntity.getReceivedContentMic().getEncodedMessageDigest(),
                "Unexpected Encoded Message Digest");
        assertEquals(
                EXPECTED_DIGEST_ALGORITHM_ID,
                messageDispositionNotificationEntity.getReceivedContentMic().getDigestAlgorithmId(),
                "Unexpected Digest Algorithm ID");
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

        JceKeyTransRecipientInfoGenerator recipientInfoGenerator =
                new JceKeyTransRecipientInfoGenerator(encryptionCertificate);
        cmsEnvelopeDataGenerator.addRecipientInfoGenerator(recipientInfoGenerator);

        //
        // Create encryptor
        //
        OutputEncryptor contentEncryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES128_CCM).build();

        //
        // Build Enveloped Entity
        //
        TextPlainEntity textEntity = new TextPlainEntity("This is a super secret messatge!", "US-ASCII", "7bit", false);
        ApplicationPkcs7MimeEnvelopedDataEntity applicationPkcs7MimeEntity =
                new ApplicationPkcs7MimeEnvelopedDataEntity(
                        textEntity, cmsEnvelopeDataGenerator, contentEncryptor, "binary", true);

        MimeEntity decryptedMimeEntity = applicationPkcs7MimeEntity.getEncryptedEntity(encryptKP.getPrivate());
        assertEquals(
                "text/plain; charset=US-ASCII",
                decryptedMimeEntity.getContentType(),
                "Decrypted entity has unexpected content type");
        assertEquals(
                "This is a super secret messatge!",
                ((TextPlainEntity) decryptedMimeEntity).getText(),
                "Decrypted entity has unexpected content");
    }

    /**
     * create a basic X509 certificate from the given keys
     */
    private X509Certificate makeCertificate(KeyPair subKP, String subDN, KeyPair issKP, String issDN)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        PublicKey subPub = subKP.getPublic();
        PrivateKey issPriv = issKP.getPrivate();
        PublicKey issPub = issKP.getPublic();

        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(
                new X500Name(issDN),
                BigInteger.valueOf(serialNo++),
                new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 100)),
                new X500Name(subDN),
                subPub);

        v3CertGen.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(subPub));

        v3CertGen.addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(issPub));

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(v3CertGen.build(new JcaContentSignerBuilder("MD5withRSA")
                        .setProvider("BC")
                        .build(issPriv)));
    }

    private AuthorityKeyIdentifier createAuthorityKeyId(PublicKey pub) throws IOException {
        SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(pub.getEncoded());

        BcX509ExtensionUtils utils = new BcX509ExtensionUtils();
        return utils.createAuthorityKeyIdentifier(info);
    }

    private DispositionNotificationMultipartReportEntity createMdnEntity(String reportContent, String boundary)
            throws Exception {
        InputStream is =
                new ByteArrayInputStream(reportContent.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer =
                new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity =
                EntityParser.parseMultipartReportEntityBody(
                        inbuffer,
                        is,
                        boundary,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING);

        assertNotNull(
                dispositionNotificationMultipartReportEntity,
                "Unexpected Null disposition notification multipart entity");

        return dispositionNotificationMultipartReportEntity;
    }

    static SubjectKeyIdentifier createSubjectKeyId(PublicKey pub) throws IOException {
        SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(pub.getEncoded());

        return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
    }
}
