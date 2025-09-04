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
package org.apache.camel.component.as2.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.entity.TextPlainEntity;
import org.apache.camel.component.as2.api.util.AS2Utils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AS2MessageTest extends AS2MessageTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(AS2MessageTest.class);

    @BeforeAll
    public static void setUpOnce() throws Exception {
        setupKeysAndCertificates();

        testServer = new AS2ServerConnection(
                AS2_VERSION, "MyServer-HTTP/1.1", SERVER_FQDN, TARGET_PORT, AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), decryptingKP.getPrivate(), MDN_MESSAGE_TEMPLATE,
                VALIDATE_SIGNING_CERTIFICATE_CHAIN, null);
        testServer.listen("*", new HttpRequestHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                    throws HttpException, IOException {
                try {
                    org.apache.camel.component.as2.api.entity.EntityParser.parseAS2MessageEntity(request);
                    context.setAttribute(AS2ServerManager.SUBJECT, SUBJECT);
                    context.setAttribute(AS2ServerManager.FROM, AS2_NAME);
                    LOG.debug("{}", AS2Utils.printMessage(request));
                    ediEntity = HttpMessageUtils.extractEdiPayload(request, new HttpMessageUtils.DecrpytingAndSigningInfo(
                            testServer.getValidateSigningCertificateChain(), testServer.getDecryptingPrivateKey()));
                } catch (Exception e) {
                    throw new HttpException("Failed to parse AS2 Message Entity", e);
                }
            }
        });
    }

    @BeforeEach
    public void setUp() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Create and populate certificate store.
        JcaCertStore certs = new JcaCertStore(certList);

        // Create capabilities vector
        SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
        capabilities.addCapability(SMIMECapability.dES_EDE3_CBC);
        capabilities.addCapability(SMIMECapability.rC2_CBC, 128);
        capabilities.addCapability(SMIMECapability.dES_CBC);

        // Create signing attributes
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(
                new IssuerAndSerialNumber(
                        new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
        attributes.add(new SMIMECapabilitiesAttribute(capabilities));

        for (String signingAlgorithmName : AS2SignedDataGenerator
                .getSupportedSignatureAlgorithmNamesForKey(signingKP.getPrivate())) {
            try {
                this.gen = new AS2SignedDataGenerator();
                this.gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC")
                        .setSignedAttributeGenerator(new AttributeTable(attributes))
                        .build(signingAlgorithmName, signingKP.getPrivate(), signingCert));
                this.gen.addCertificates(certs);
                break;
            } catch (Exception e) {
                this.gen = null;
                continue;
            }
        }

        if (this.gen == null) {
            throw new Exception("failed to create signing generator");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true,false,false", "true,false,true", "true,true,false", "true,true,true" })
    void encryptedBinaryContentTransferEncodingTest(boolean encrypt, boolean sign, boolean compress) throws IOException {
        binaryContentTransferEncodingTest(encrypt, sign, compress);
    }

    @Test
    public void multipartSignedMessageRequestTest() throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII),
                null, AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity signedEntity = (MultipartSignedEntity) entity;
        assertTrue(signedEntity.isMainBody(), "Entity not set as main body of request");
        assertEquals(2, signedEntity.getPartCount(), "Request contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(signedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) signedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(signedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) signedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");
    }

    @Test
    public void aes128CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES128_CBC);
    }

    @Test
    public void aes192CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES192_CBC);
    }

    @Test
    public void aes256CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES256_CBC);
    }

    @Test
    public void aes128CcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES128_CCM);
    }

    @Test
    public void aes192CcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES192_CCM);
    }

    @Test
    public void aes256CcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES256_CCM);
    }

    @Test
    public void aes128GcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES128_GCM);
    }

    @Test
    public void aes192GcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES192_GCM);
    }

    @Test
    public void aes256GcmEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.AES256_GCM);
    }

    @Test
    public void camellia128CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.CAMELLIA128_CBC);
    }

    @Test
    public void camellia192CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.CAMELLIA192_CBC);
    }

    @Test
    public void camellia256CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.CAMELLIA256_CBC);
    }

    @Test
    public void cast5CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.CAST5_CBC);
    }

    @Test
    public void desCbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.DES_CBC);
    }

    @Test
    public void desEde3CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.DES_EDE3_CBC);
    }

    @Test
    public void cost28147GcfbEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.GOST28147_GCFB);
    }

    @Test
    public void ideaCbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.IDEA_CBC);
    }

    @Test
    public void rc2CbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.RC2_CBC);
    }

    @Test
    public void rc4EnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.RC4);
    }

    @Test
    public void seedCbcEnvelopedMessageTest() throws Exception {
        envelopedMessageTest(AS2EncryptionAlgorithm.SEED_CBC);
    }

    public void envelopedMessageTest(AS2EncryptionAlgorithm encryptionAlgorithm) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        LOG.info("Key Algorithm: {}", signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(), null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, encryptionAlgorithm,
                certList.toArray(new Certificate[0]), "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) encryptedEntity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for enveloped mime part");
        assertFalse(ediEntity.isMainBody(), "Enveloped mime type set as main body of request");

        assertTrue(ediEntity.getEdiMessage() instanceof String);
        assertEquals(EDI_MESSAGE, ((String) ediEntity.getEdiMessage()).replaceAll("\r", ""),
                "Unexpected content for enveloped mime part");
    }

    @Test
    public void aes128CbcEnvelopedAndSignedMessageTest() throws Exception {
        envelopedAndSignedMessageTest(AS2EncryptionAlgorithm.AES128_CBC);
    }

    public void envelopedAndSignedMessageTest(AS2EncryptionAlgorithm encryptionAlgorithm) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        LOG.info("Key Algorithm: {}", signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED_ENCRYPTED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(), null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, encryptionAlgorithm,
                certList.toArray(new Certificate[0]), "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) encryptedEntity;
        assertTrue(multipartSignedEntity.getContentType().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for enveloped mime part");
        assertFalse(multipartSignedEntity.isMainBody(), "Enveloped mime type set as main body of request");
        assertEquals(2, multipartSignedEntity.getPartCount(), "Request contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity,
                "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");

    }

    @Test
    public void signatureVerificationTest() throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII),
                null, AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
        MimeEntity signedEntity = multipartSignedEntity.getSignedDataEntity();
        assertTrue(signedEntity instanceof ApplicationEntity, "Signed entity wrong type");
        ApplicationEntity ediMessageEntity = (ApplicationEntity) signedEntity;
        assertNotNull(ediMessageEntity, "Multipart signed entity does not contain EDI message entity");
        ApplicationPkcs7SignatureEntity signatureEntity = multipartSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Multipart signed entity does not contain signature entity");

        // Validate Signature
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }), "Signature is invalid");

    }

    @Test
    public void asynchronousMdnMessageTest() throws Exception {

        AS2AsynchronousMDNManager mdnManager = new AS2AsynchronousMDNManager(
                AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                certList.toArray(new X509Certificate[0]), signingKP.getPrivate());

        // Create plain edi request message to acknowledge
        ApplicationEntity ediEntity = EntityUtils.createEDIEntity(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII),
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null, false, "filename.txt");
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", REQUEST_URI);
        HttpMessageUtils.setHeaderValue(request, AS2Header.SUBJECT, SUBJECT);
        String httpdate = DATE_GENERATOR.getCurrentDate();
        HttpMessageUtils.setHeaderValue(request, AS2Header.DATE, httpdate);
        HttpMessageUtils.setHeaderValue(request, AS2Header.AS2_TO, AS2_NAME);
        HttpMessageUtils.setHeaderValue(request, AS2Header.AS2_FROM, AS2_NAME);
        String originalMessageId = AS2Utils.createMessageId(SERVER_FQDN);
        HttpMessageUtils.setHeaderValue(request, AS2Header.MESSAGE_ID, originalMessageId);
        HttpMessageUtils.setHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS,
                DISPOSITION_NOTIFICATION_OPTIONS);
        EntityUtils.setMessageEntity(request, ediEntity);

        // Create response for MDN creation.
        HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(new ProtocolVersion("HTTP", 1, 1));
        httpdate = DATE_GENERATOR.getCurrentDate();
        response.setHeader(AS2Header.DATE, httpdate);
        response.setHeader(AS2Header.SERVER, REPORTING_UA);

        // Create a receipt for edi message
        Map<String, String> extensionFields = new HashMap<>();
        extensionFields.put("Original-Recipient", "rfc822;" + AS2_NAME);
        AS2DispositionModifier dispositionModifier = AS2DispositionModifier.createWarning("AS2 is cool!");
        String[] failureFields = new String[] { "failure-field-1" };
        String[] errorFields = new String[] { "error-field-1" };
        String[] warningFields = new String[] { "warning-field-1" };
        DispositionNotificationMultipartReportEntity mdn = new DispositionNotificationMultipartReportEntity(
                request,
                response, DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, AS2DispositionType.PROCESSED,
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, null, "boundary", true,
                null, "Got ya message!", null);

        // Send MDN
        HttpCoreContext httpContext = mdnManager.send(mdn, mdn.getMainMessageContentType(), RECIPIENT_DELIVERY_ADDRESS);
        HttpRequest mndRequest = httpContext.getRequest();
        Arrays.stream(request.getHeaders(AS2Header.CONTENT_DISPOSITION)).forEach(System.out::println);
        DispositionNotificationMultipartReportEntity reportEntity
                = HttpMessageUtils.getEntity(mndRequest, DispositionNotificationMultipartReportEntity.class);
        assertNotNull(reportEntity, "Request does not contain report");
        assertEquals(2, reportEntity.getPartCount(), "Report entity contains invalid number of parts");
        assertTrue(reportEntity.getPart(0) instanceof TextPlainEntity, "Report first part is not text entity");
        assertTrue(reportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity,
                "Report second part is not MDN entity");
        AS2MessageDispositionNotificationEntity mdnEntity = (AS2MessageDispositionNotificationEntity) reportEntity.getPart(1);
        assertEquals(REPORTING_UA, mdnEntity.getReportingUA(), "Unexpected value for Reporting UA");
        assertEquals(AS2_NAME, mdnEntity.getFinalRecipient(), "Unexpected value for Final Recipient");
        assertEquals(originalMessageId, mdnEntity.getOriginalMessageId(), "Unexpected value for Original Message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, mdnEntity.getDispositionMode(),
                "Unexpected value for Disposition Mode");
        assertEquals(AS2DispositionType.PROCESSED, mdnEntity.getDispositionType(), "Unexpected value for Disposition Type");
        assertEquals(dispositionModifier, mdnEntity.getDispositionModifier(), "Unexpected value for Disposition Modifier");
        assertArrayEquals(failureFields, mdnEntity.getFailureFields(), "Unexpected value for Failure Fields");
        assertArrayEquals(errorFields, mdnEntity.getErrorFields(), "Unexpected value for Error Fields");
        assertArrayEquals(warningFields, mdnEntity.getWarningFields(), "Unexpected value for Warning Fields");
        assertEquals(extensionFields, mdnEntity.getExtensionFields(), "Unexpected value for Extension Fields");
        ReceivedContentMic expectedMic = MicUtils.createReceivedContentMic(request, null, null);
        ReceivedContentMic mdnMic = mdnEntity.getReceivedContentMic();
        assertEquals(expectedMic.getEncodedMessageDigest(), mdnMic.getEncodedMessageDigest(),
                "Unexpected value for Received Content Mic");
        LOG.debug("\r\n{}", AS2Utils.printMessage(mndRequest));
    }

    @Test
    public void signedAndCompressedMessageTest() throws Exception {
        signedAndCompressedMessage(EDI_MESSAGE);
    }

    @Test
    public void signedAndCompressedStreamMessageTest() throws Exception {
        signedAndCompressedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)));
    }

    private void signedAndCompressedMessage(Object msg) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        LOG.info("Key Algorithm: {}", signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), "base64",
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null,
                null, "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity) entity;
        assertTrue(compressedDataEntity.isMainBody(), "Entity not set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) compressedEntity;
        assertTrue(multipartSignedEntity.getContentType().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for compressed entity");
        assertFalse(multipartSignedEntity.isMainBody(), "Multipart signed entity set as main body of request");
        assertEquals(2, multipartSignedEntity.getPartCount(), "Multipart signed entity contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity,
                "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");
    }

    @Test
    public void envelopedAndCompressedMessageTest() throws Exception {
        envelopedAndCompressedMessage(EDI_MESSAGE);
    }

    @Test
    public void envelopedAndCompressedStreamMessageTest() throws Exception {
        envelopedAndCompressedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)));
    }

    private void envelopedAndCompressedMessage(Object msg) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        LOG.info("Key Algorithm: {}", signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), "base64", null, null, null,
                AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                AS2EncryptionAlgorithm.AES128_CBC, certList.toArray(new Certificate[0]), "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Enveloped mime part incorrect type ");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity
                = (ApplicationPkcs7MimeCompressedDataEntity) encryptedEntity;
        assertTrue(compressedDataEntity.getContentType().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for compressed mime part");
        assertFalse(compressedDataEntity.isMainBody(), "Enveloped mime type set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) compressedEntity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for compressed entity");
        assertFalse(ediEntity.isMainBody(), "Compressed entity set as main body of request");

        assertTrue(ediEntity.getEdiMessage() instanceof InputStream);
        InputStream is = (InputStream) ediEntity.getEdiMessage();
        assertEquals(EDI_MESSAGE, new String(is.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\r", ""),
                "EDI message does not match");
    }

    // Verify that the payload is compressed before being signed.
    @Test
    public void compressedAndSignedMessageTest() throws Exception {
        compressedAndSignedMessage(EDI_MESSAGE);
    }

    @Test
    public void compressedAndSignedStreamMessageTest() throws Exception {
        compressedAndSignedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)));
    }

    private void compressedAndSignedMessage(Object msg) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();
        HttpCoreContext httpContext = clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.COMPRESSED_SIGNED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), "base64",
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null,
                null, "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        verifyRequest(request);

        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.MULTIPART_SIGNED),
                "Unexpected content type for message");
        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
        assertTrue(multipartSignedEntity.isMainBody(), "Entity not set as main body of request");

        verifyCompressedSignedEntity(multipartSignedEntity);
    }

    // Verify that the payload is compressed before being signed and encrypted.
    @Test
    public void envelopedSignedAndCompressedMessageTest() throws Exception {
        envelopedSignedAndCompressedMessage(EDI_MESSAGE);
    }

    @Test
    public void envelopedSignedAndCompressedStreamMessageTest() throws Exception {
        envelopedSignedAndCompressedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)));
    }

    private void envelopedSignedAndCompressedMessage(Object msg) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();
        HttpCoreContext httpContext = clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_COMPRESSED_SIGNED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                AS2EncryptionAlgorithm.AES128_CBC, certList.toArray(new Certificate[0]), "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        verifyRequest(request);

        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");
        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity decryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(decryptedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) decryptedEntity;
        assertTrue(multipartSignedEntity.getContentType().startsWith(AS2MimeType.MULTIPART_SIGNED),
                "Unexpected content type for compressed mime part");
        assertFalse(multipartSignedEntity.isMainBody(), "Enveloped mime type set as main body of request");

        verifyCompressedSignedEntity(multipartSignedEntity);
    }

    @Test
    public void envelopedCompressedAndSignedMessageTest() throws Exception {
        envelopedCompressedAndSignedMessage(EDI_MESSAGE);
    }

    @Test
    public void envelopedCompressedAndSignedStreamMessageTest() throws Exception {
        envelopedCompressedAndSignedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)));
    }

    private void envelopedCompressedAndSignedMessage(Object msg) throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        LOG.info("Key Algorithm: {}", signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_SIGNED_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                AS2EncryptionAlgorithm.AES128_CBC, certList.toArray(new Certificate[0]), "file.txt", null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Enveloped mime part incorrect type ");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity
                = (ApplicationPkcs7MimeCompressedDataEntity) encryptedEntity;
        assertTrue(compressedDataEntity.getContentType().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME),
                "Unexpected content type for compressed mime part");
        assertFalse(compressedDataEntity.isMainBody(), "Enveloped mime type set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) compressedEntity;
        assertTrue(multipartSignedEntity.getContentType().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for compressed entity");
        assertFalse(multipartSignedEntity.isMainBody(), "Multipart signed entity set as main body of request");
        assertEquals(2, multipartSignedEntity.getPartCount(), "Multipart signed entity contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity,
                "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");
    }

    @ParameterizedTest
    @CsvSource({ "true,false", "true,true" })
    void encryptedCompressionSignatureOrderTest(boolean encrypt, boolean compressBeforeSign) throws IOException {
        compressionSignatureOrderTest(encrypt, compressBeforeSign);
    }

    private void verifyRequest(HttpRequest request) throws URISyntaxException {
        assertEquals(METHOD, request.getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getUri().getPath(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"),
                "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(),
                "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
    }

    private void verifyCompressedSignedEntity(MultipartSignedEntity multipartSignedEntity) throws HttpException {
        assertEquals(2, multipartSignedEntity.getPartCount(), "Request contains invalid number of mime parts");

        // Verify first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationPkcs7MimeCompressedDataEntity,
                "First mime part incorrect type ");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity
                = (ApplicationPkcs7MimeCompressedDataEntity) multipartSignedEntity.getPart(0);
        assertTrue(compressedDataEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_MIME_COMPRESSED),
                "Unexpected content type for first mime part");
        assertFalse(compressedDataEntity.isMainBody(), "First mime type set as main body of request");

        // Verify compressed entity.
        verifyEdiFactEntity(compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider()));

        // Verify second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity,
                "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");

        // Verify Signature
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }), "Signature must be invalid");
    }

    private void verifyEdiFactEntity(MimeEntity entity) {
        assertTrue(entity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for compressed entity");
    }
}
