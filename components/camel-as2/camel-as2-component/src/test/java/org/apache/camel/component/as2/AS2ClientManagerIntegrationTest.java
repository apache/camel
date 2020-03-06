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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2AsynchronousMDNManager;
import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.AS2Utils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.camel.component.as2.internal.AS2ApiCollection;
import org.apache.camel.component.as2.internal.AS2ClientManagerApiMethod;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpDateGenerator;
import org.apache.http.protocol.HttpRequestHandler;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.as2.api.AS2ClientManager} APIs.
 */
public class AS2ClientManagerIntegrationTest extends AbstractAS2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ClientManagerIntegrationTest.class);
    private static final String PATH_PREFIX = AS2ApiCollection.getCollection().getApiName(AS2ClientManagerApiMethod.class).getName();

    private static final String SERVER_FQDN = "server.example.com";
    private static final String ORIGIN_SERVER_NAME = "AS2ClientManagerIntegrationTest Server";
    private static final String AS2_VERSION = "1.1";
    private static final String REQUEST_URI = "/";
    private static final String SUBJECT = "Test Case";
    private static final String AS2_NAME = "878051556";
    private static final String FROM = "mrAS@example.org";

    private static final String MDN_FROM = "as2Test@server.example.com";
    private static final String MDN_SUBJECT_PREFIX = "MDN Response:";

    private static final String EDI_MESSAGE =
            "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
            + "UNH+00000000000117+INVOIC:D:97B:UN'\n"
            + "BGM+380+342459+9'\n"
            + "DTM+3:20060515:102'\n"
            + "RFF+ON:521052'\n"
            + "NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
            + "NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
            + "CUX+1:USD'\n"
            + "LIN+1++157870:IN'\n"
            + "IMD+F++:::WIDGET'\n"
            + "QTY+47:1020:EA'\n"
            + "ALI+US'\n"
            + "MOA+203:1202.58'\n"
            + "PRI+INV:1.179'\n"
            + "LIN+2++157871:IN'\n"
            + "IMD+F++:::DIFFERENT WIDGET'\n"
            + "QTY+47:20:EA'\n"
            + "ALI+JP'\n"
            + "MOA+203:410'\n"
            + "PRI+INV:20.5'\n"
            + "UNS+S'\n"
            + "MOA+39:2137.58'\n"
            + "ALC+C+ABG'\n"
            + "MOA+8:525'\n"
            + "UNT+23+00000000000117'\n"
            + "UNZ+1+00000000000778'\n";

    private static final String EDI_MESSAGE_CONTENT_TRANSFER_ENCODING = "7bit";
    private static final String EXPECTED_AS2_VERSION = AS2_VERSION;
    private static final String EXPECTED_MDN_SUBJECT = MDN_SUBJECT_PREFIX + SUBJECT;
    private static final String[] SIGNED_RECEIPT_MIC_ALGORITHMS = new String[] {"sha1", "md5"};
    private static final String DISPOSITION_NOTIFICATION_OPTIONS = "signed-receipt-protocol=optional,pkcs7-signature; signed-receipt-micalg=optional,sha1";
    private static final int PARTNER_TARGET_PORT = 8888;
    private static final int MDN_TARGET_PORT = AvailablePortFinder.getNextAvailable();
    private static final String RECIPIENT_DELIVERY_ADDRESS = "http://localhost:" + MDN_TARGET_PORT + "/handle-receipts";
    private static final String REPORTING_UA = "Server Responding with MDN";

    private static AS2ServerConnection serverConnection;
    private static KeyPair serverSigningKP;
    private static List<X509Certificate> serverCertList;
    private static RequestHandler requestHandler;

    private static final HttpDateGenerator DATE_GENERATOR = new HttpDateGenerator();

    private KeyPair issueKP;
    private X509Certificate issueCert;

    private KeyPair signingKP;
    private KeyPair decryptingKP;
    private X509Certificate signingCert;
    private List<X509Certificate> certList;
    private AS2SignedDataGenerator gen;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Security.addProvider(new BouncyCastleProvider());

        setupKeysAndCertificates();

        // Create and populate certificate store.
        JcaCertStore certs = new JcaCertStore(certList);

        // Create capabilities vector
        SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
        capabilities.addCapability(SMIMECapability.dES_EDE3_CBC);
        capabilities.addCapability(SMIMECapability.rC2_CBC, 128);
        capabilities.addCapability(SMIMECapability.dES_CBC);

        // Create signing attributes
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(new IssuerAndSerialNumber(new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
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

    @Test
    public void plainMessageSendTest() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelAS2.requestUri", REQUEST_URI);
        // parameter type is String
        headers.put("CamelAS2.subject", SUBJECT);
        // parameter type is String
        headers.put("CamelAS2.from", FROM);
        // parameter type is String
        headers.put("CamelAS2.as2From", AS2_NAME);
        // parameter type is String
        headers.put("CamelAS2.as2To", AS2_NAME);
        // parameter type is org.apache.camel.component.as2.api.AS2MessageStructure
        headers.put("CamelAS2.as2MessageStructure", AS2MessageStructure.PLAIN);
        // parameter type is org.apache.http.entity.ContentType
        headers.put("CamelAS2.ediMessageContentType", ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII));
        // parameter type is String
        headers.put("CamelAS2.ediMessageTransferEncoding", EDI_MESSAGE_CONTENT_TRANSFER_ENCODING);
        // parameter type is String
        headers.put("CamelAS2.dispositionNotificationTo", "mrAS2@example.com");

        final Triple<HttpEntity, HttpRequest, HttpResponse> result = executeRequest(headers);
        HttpEntity responseEntity = result.getLeft();
        HttpRequest request = result.getMiddle();
        HttpResponse response = result.getRight();

        assertNotNull(result, "send result");
        LOG.debug("send: " + result);
        assertNotNull(request, "Request");
        assertTrue(request instanceof HttpEntityEnclosingRequest, "Request does not contain body");
        HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request body");
        assertTrue(entity instanceof ApplicationEDIEntity, "Request body does not contain EDI entity");
        String ediMessage = ((ApplicationEDIEntity)entity).getEdiMessage();
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediMessage.replaceAll("[\n\r]", ""), "EDI message is different");

        assertNotNull(response, "Response");
        assertTrue(HttpMessageUtils.getHeaderValue(response, AS2Header.CONTENT_TYPE).startsWith(AS2MimeType.MULTIPART_REPORT), "Unexpected response type");
        assertEquals(AS2Constants.MIME_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.MIME_VERSION), "Unexpected mime version");
        assertEquals(EXPECTED_AS2_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_VERSION), "Unexpected AS2 version");
        assertEquals(EXPECTED_MDN_SUBJECT, HttpMessageUtils.getHeaderValue(response, AS2Header.SUBJECT), "Unexpected MDN subject");
        assertEquals(MDN_FROM, HttpMessageUtils.getHeaderValue(response, AS2Header.FROM), "Unexpected MDN from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_FROM), "Unexpected AS2 from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_TO), "Unexpected AS2 to");
        assertNotNull(HttpMessageUtils.getHeaderValue(response, AS2Header.MESSAGE_ID), "Missing message id");

        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof DispositionNotificationMultipartReportEntity, "Unexpected response entity type");
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue(), "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(), secondPart.getContentTypeValue(),
                     "Unexpected content type in second body part of report");

        assertTrue(secondPart instanceof AS2MessageDispositionNotificationEntity, "");
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = (AS2MessageDispositionNotificationEntity) secondPart;
        assertEquals(ORIGIN_SERVER_NAME, messageDispositionNotificationEntity.getReportingUA(), "Unexpected value for reporting UA");
        assertEquals(AS2_NAME, messageDispositionNotificationEntity.getFinalRecipient(), "Unexpected value for final recipient");
        assertEquals(HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID), messageDispositionNotificationEntity.getOriginalMessageId(), "Unexpected value for original message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, messageDispositionNotificationEntity.getDispositionMode(), "Unexpected value for disposition mode");
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType(), "Unexpected value for disposition type");
        
    }

    @Test
    public void envelopedMessageSendTest() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelAS2.requestUri", REQUEST_URI);
        // parameter type is String
        headers.put("CamelAS2.subject", SUBJECT);
        // parameter type is String
        headers.put("CamelAS2.from", FROM);
        // parameter type is String
        headers.put("CamelAS2.as2From", AS2_NAME);
        // parameter type is String
        headers.put("CamelAS2.as2To", AS2_NAME);
        // parameter type is org.apache.camel.component.as2.api.AS2MessageStructure
        headers.put("CamelAS2.as2MessageStructure", AS2MessageStructure.ENCRYPTED);
        // parameter type is org.apache.http.entity.ContentType
        headers.put("CamelAS2.ediMessageContentType", ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII));
        // parameter type is String
        headers.put("CamelAS2.ediMessageTransferEncoding", EDI_MESSAGE_CONTENT_TRANSFER_ENCODING);
        // parameter type is String
        headers.put("CamelAS2.dispositionNotificationTo", "mrAS2@example.com");
        // parameter type is org.apache.camel.component.as2.api.AS2EncryptionAlgorithm
        headers.put("CamelAS2.encryptingAlgorithm", AS2EncryptionAlgorithm.AES128_CBC);
        // parameter type is java.security.cert.Certificate[]
        headers.put("CamelAS2.encryptingCertificateChain", certList);

        final Triple<HttpEntity, HttpRequest, HttpResponse> result = executeRequest(headers);
        HttpEntity responseEntity = result.getLeft();
        HttpRequest request = result.getMiddle();
        HttpResponse response = result.getRight();

        assertNotNull(result, "send result");
        LOG.debug("send: " + result);

        assertNotNull(request, "Request");
        assertTrue(request instanceof HttpEntityEnclosingRequest, "Request does not contain body");
        HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request body");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Request body does not contain ApplicationPkcs7Mime entity");
        MimeEntity envelopeEntity = ((ApplicationPkcs7MimeEnvelopedDataEntity)entity).getEncryptedEntity(decryptingKP.getPrivate());
        assertTrue(envelopeEntity instanceof ApplicationEDIEntity, "Enveloped entity is not an EDI entity");
        String ediMessage = ((ApplicationEDIEntity)envelopeEntity).getEdiMessage();
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediMessage.replaceAll("[\n\r]", ""), "EDI message is different");

        assertNotNull(response, "Response");
        assertTrue(HttpMessageUtils.getHeaderValue(response, AS2Header.CONTENT_TYPE).startsWith(AS2MimeType.MULTIPART_REPORT), "Unexpected response type");
        assertEquals(AS2Constants.MIME_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.MIME_VERSION), "Unexpected mime version");
        assertEquals(EXPECTED_AS2_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_VERSION), "Unexpected AS2 version");
        assertEquals(EXPECTED_MDN_SUBJECT, HttpMessageUtils.getHeaderValue(response, AS2Header.SUBJECT), "Unexpected MDN subject");
        assertEquals(MDN_FROM, HttpMessageUtils.getHeaderValue(response, AS2Header.FROM), "Unexpected MDN from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_FROM), "Unexpected AS2 from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_TO), "Unexpected AS2 to");
        assertNotNull(HttpMessageUtils.getHeaderValue(response, AS2Header.MESSAGE_ID), "Missing message id");

        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof DispositionNotificationMultipartReportEntity, "Unexpected response entity type");
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue(), "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(), secondPart.getContentTypeValue(),
                     "Unexpected content type in second body part of report");

        assertTrue(secondPart instanceof AS2MessageDispositionNotificationEntity, "");
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = (AS2MessageDispositionNotificationEntity) secondPart;
        assertEquals(ORIGIN_SERVER_NAME, messageDispositionNotificationEntity.getReportingUA(), "Unexpected value for reporting UA");
        assertEquals(AS2_NAME, messageDispositionNotificationEntity.getFinalRecipient(), "Unexpected value for final recipient");
        assertEquals(HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID), messageDispositionNotificationEntity.getOriginalMessageId(), "Unexpected value for original message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, messageDispositionNotificationEntity.getDispositionMode(), "Unexpected value for disposition mode");
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType(), "Unexpected value for disposition type");
        
    }

    @Test
    public void multipartSignedMessageTest() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelAS2.requestUri", REQUEST_URI);
        // parameter type is String
        headers.put("CamelAS2.subject", SUBJECT);
        // parameter type is String
        headers.put("CamelAS2.from", FROM);
        // parameter type is String
        headers.put("CamelAS2.as2From", AS2_NAME);
        // parameter type is String
        headers.put("CamelAS2.as2To", AS2_NAME);
        // parameter type is org.apache.camel.component.as2.api.AS2MessageStructure
        headers.put("CamelAS2.as2MessageStructure", AS2MessageStructure.SIGNED);
        // parameter type is org.apache.http.entity.ContentType
        headers.put("CamelAS2.ediMessageContentType", ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII));
        // parameter type is String
        headers.put("CamelAS2.ediMessageTransferEncoding", EDI_MESSAGE_CONTENT_TRANSFER_ENCODING);
        // parameter type is org.apache.camel.component.as2.api.AS2SignatureAlgorithm
        headers.put("CamelAS2.signingAlgorithm", AS2SignatureAlgorithm.SHA512WITHRSA);
        // parameter type is java.security.cert.Certificate[]
        headers.put("CamelAS2.signingCertificateChain", certList.toArray(new Certificate[0]));
        // parameter type is java.security.PrivateKey
        headers.put("CamelAS2.signingPrivateKey", signingKP.getPrivate());
        // parameter type is String
        headers.put("CamelAS2.dispositionNotificationTo", "mrAS2@example.com");
        // parameter type is String[]
        headers.put("CamelAS2.signedReceiptMicAlgorithms", SIGNED_RECEIPT_MIC_ALGORITHMS);

        final Triple<HttpEntity, HttpRequest, HttpResponse> result = executeRequest(headers);
        HttpEntity responseEntity = result.getLeft();
        HttpRequest request = result.getMiddle();
        HttpResponse response = result.getRight();

        assertNotNull(result, "send result");
        LOG.debug("send: " + result);
        assertNotNull(request, "Request");
        assertTrue(request instanceof HttpEntityEnclosingRequest, "Request does not contain body");
        HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request body");
        assertTrue(entity instanceof MultipartSignedEntity, "Request body does not contain EDI entity");

        MimeEntity signedEntity = ((MultipartSignedEntity)entity).getSignedDataEntity();
        assertTrue(signedEntity instanceof ApplicationEDIEntity, "Signed entity wrong type");
        ApplicationEDIEntity ediMessageEntity = (ApplicationEDIEntity) signedEntity;
        String ediMessage = ediMessageEntity.getEdiMessage();
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediMessage.replaceAll("[\n\r]", ""), "EDI message is different");

        assertNotNull(response, "Response");
        String contentTypeHeaderValue = HttpMessageUtils.getHeaderValue(response, AS2Header.CONTENT_TYPE);
        ContentType responseContentType = ContentType.parse(contentTypeHeaderValue);
        assertEquals(AS2MimeType.MULTIPART_SIGNED, responseContentType.getMimeType(), "Unexpected response type");
        assertEquals(AS2Constants.MIME_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.MIME_VERSION), "Unexpected mime version");
        assertEquals(EXPECTED_AS2_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_VERSION), "Unexpected AS2 version");
        assertEquals(EXPECTED_MDN_SUBJECT, HttpMessageUtils.getHeaderValue(response, AS2Header.SUBJECT), "Unexpected MDN subject");
        assertEquals(MDN_FROM, HttpMessageUtils.getHeaderValue(response, AS2Header.FROM), "Unexpected MDN from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_FROM), "Unexpected AS2 from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_TO), "Unexpected AS2 to");
        assertNotNull(HttpMessageUtils.getHeaderValue(response, AS2Header.MESSAGE_ID), "Missing message id");

        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof MultipartSignedEntity, "Unexpected response entity type");
        MultipartSignedEntity responseSignedEntity = (MultipartSignedEntity) responseEntity;
        assertTrue(responseSignedEntity.isValid(), "Signature for response entity is invalid");
        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity, "Signed entity wrong type");
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseSignedDataEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue(), "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(), secondPart.getContentTypeValue(),
                     "Unexpected content type in second body part of report");
        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");
        
        assertTrue(secondPart instanceof AS2MessageDispositionNotificationEntity, "");
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = (AS2MessageDispositionNotificationEntity) secondPart;
        assertEquals(ORIGIN_SERVER_NAME, messageDispositionNotificationEntity.getReportingUA(), "Unexpected value for reporting UA");
        assertEquals(AS2_NAME, messageDispositionNotificationEntity.getFinalRecipient(), "Unexpected value for final recipient");
        assertEquals(HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID), messageDispositionNotificationEntity.getOriginalMessageId(), "Unexpected value for original message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, messageDispositionNotificationEntity.getDispositionMode(), "Unexpected value for disposition mode");
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType(), "Unexpected value for disposition type");

        ReceivedContentMic receivedContentMic = messageDispositionNotificationEntity.getReceivedContentMic();
        ReceivedContentMic computedContentMic = MicUtils.createReceivedContentMic((HttpEntityEnclosingRequest)request, decryptingKP.getPrivate());
        assertEquals(computedContentMic.getEncodedMessageDigest(), receivedContentMic.getEncodedMessageDigest(), "Received content MIC does not match computed");
    }

    @Test
    public void compressedMessageTest() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelAS2.requestUri", REQUEST_URI);
        // parameter type is String
        headers.put("CamelAS2.subject", SUBJECT);
        // parameter type is String
        headers.put("CamelAS2.from", FROM);
        // parameter type is String
        headers.put("CamelAS2.as2From", AS2_NAME);
        // parameter type is String
        headers.put("CamelAS2.as2To", AS2_NAME);
        // parameter type is org.apache.camel.component.as2.api.AS2MessageStructure
        headers.put("CamelAS2.as2MessageStructure", AS2MessageStructure.PLAIN_COMPRESSED);
        // parameter type is org.apache.http.entity.ContentType
        headers.put("CamelAS2.ediMessageContentType", ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII));
        // parameter type is String
        headers.put("CamelAS2.ediMessageTransferEncoding", EDI_MESSAGE_CONTENT_TRANSFER_ENCODING);
        // parameter type is org.apache.camel.component.as2.api.AS2CompressionAlgorithm
        headers.put("CamelAS2.compressionAlgorithm", AS2CompressionAlgorithm.ZLIB);
        // parameter type is String
        headers.put("CamelAS2.dispositionNotificationTo", "mrAS2@example.com");
        // parameter type is String[]
        headers.put("CamelAS2.signedReceiptMicAlgorithms", SIGNED_RECEIPT_MIC_ALGORITHMS);

        final Triple<HttpEntity, HttpRequest, HttpResponse> result = executeRequest(headers);
        HttpEntity responseEntity = result.getLeft();
        HttpRequest request = result.getMiddle();
        HttpResponse response = result.getRight();

        assertNotNull(result, "send result");
        LOG.debug("send: " + result);
        assertNotNull(request, "Request");
        assertTrue(request instanceof HttpEntityEnclosingRequest, "Request does not contain body");
        HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request body");
        assertTrue(entity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Request body does not contain EDI entity");

        MimeEntity compressedEntity = ((ApplicationPkcs7MimeCompressedDataEntity)entity).getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof ApplicationEDIEntity, "Signed entity wrong type");
        ApplicationEDIEntity ediMessageEntity = (ApplicationEDIEntity) compressedEntity;
        String ediMessage = ediMessageEntity.getEdiMessage();
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediMessage.replaceAll("[\n\r]", ""), "EDI message is different");

        assertNotNull(response, "Response");
        String contentTypeHeaderValue = HttpMessageUtils.getHeaderValue(response, AS2Header.CONTENT_TYPE);
        ContentType responseContentType = ContentType.parse(contentTypeHeaderValue);
        assertEquals(AS2MimeType.MULTIPART_SIGNED, responseContentType.getMimeType(), "Unexpected response type");
        assertEquals(AS2Constants.MIME_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.MIME_VERSION), "Unexpected mime version");
        assertEquals(EXPECTED_AS2_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_VERSION), "Unexpected AS2 version");
        assertEquals(EXPECTED_MDN_SUBJECT, HttpMessageUtils.getHeaderValue(response, AS2Header.SUBJECT), "Unexpected MDN subject");
        assertEquals(MDN_FROM, HttpMessageUtils.getHeaderValue(response, AS2Header.FROM), "Unexpected MDN from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_FROM), "Unexpected AS2 from");
        assertEquals(AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_TO), "Unexpected AS2 to");
        assertNotNull(HttpMessageUtils.getHeaderValue(response, AS2Header.MESSAGE_ID), "Missing message id");

        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof MultipartSignedEntity, "Unexpected response entity type");
        MultipartSignedEntity responseSignedEntity = (MultipartSignedEntity) responseEntity;
        assertTrue(responseSignedEntity.isValid(), "Signature for response entity is invalid");
        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity, "Signed entity wrong type");
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseSignedDataEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue(), "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(), secondPart.getContentTypeValue(),
                     "Unexpected content type in second body part of report");
        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");

        assertTrue(secondPart instanceof AS2MessageDispositionNotificationEntity, "");
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = (AS2MessageDispositionNotificationEntity) secondPart;
        assertEquals(ORIGIN_SERVER_NAME, messageDispositionNotificationEntity.getReportingUA(), "Unexpected value for reporting UA");
        assertEquals(AS2_NAME, messageDispositionNotificationEntity.getFinalRecipient(), "Unexpected value for final recipient");
        assertEquals(HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID), messageDispositionNotificationEntity.getOriginalMessageId(), "Unexpected value for original message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, messageDispositionNotificationEntity.getDispositionMode(), "Unexpected value for disposition mode");
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType(), "Unexpected value for disposition type");

        ReceivedContentMic receivedContentMic = messageDispositionNotificationEntity.getReceivedContentMic();
        ReceivedContentMic computedContentMic = MicUtils.createReceivedContentMic((HttpEntityEnclosingRequest)request, decryptingKP.getPrivate());
        assertEquals(computedContentMic.getEncodedMessageDigest(), receivedContentMic.getEncodedMessageDigest(), "Received content MIC does not match computed");
    }

    @Test
    public void asyncMDNTest() throws Exception {
        AS2AsynchronousMDNManager mdnManager = new AS2AsynchronousMDNManager(AS2_VERSION, ORIGIN_SERVER_NAME, SERVER_FQDN,
                certList.toArray(new X509Certificate[0]), signingKP.getPrivate());

        // Create plain edi request message to acknowledge
        ApplicationEDIEntity ediEntity = EntityUtils.createEDIEntity(EDI_MESSAGE,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), null, false);
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", REQUEST_URI);
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
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        httpdate = DATE_GENERATOR.getCurrentDate();
        response.setHeader(AS2Header.DATE, httpdate);
        response.setHeader(AS2Header.SERVER, REPORTING_UA);

        // Create a receipt for edi message
        Map<String, String> extensionFields = new HashMap<>();
        extensionFields.put("Original-Recipient", "rfc822;" + AS2_NAME);
        AS2DispositionModifier dispositionModifier = AS2DispositionModifier.createWarning("AS2 is cool!");
        String[] failureFields = new String[] {"failure-field-1"};
        String[] errorFields = new String[] {"error-field-1"};
        String[] warningFields = new String[] {"warning-field-1"};
        DispositionNotificationMultipartReportEntity mdn = new DispositionNotificationMultipartReportEntity(request,
                response, DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, AS2DispositionType.PROCESSED,
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, null, "boundary",
                true, serverSigningKP.getPrivate(), "Got your message!");

        // Send MDN
        @SuppressWarnings("unused")
        HttpCoreContext httpContext = mdnManager.send(mdn, RECIPIENT_DELIVERY_ADDRESS);
        
    }
    
    @BeforeAll
    public static void setupTest() throws Exception {
        setupServerKeysAndCertificates();
        receiveTestMessages();
    }

    @AfterAll
    public static void teardownTest() throws Exception {
        if (serverConnection != null) {
            serverConnection.stopListening("/");
            serverConnection.close();
        }
    }

    public static class RequestHandler implements HttpRequestHandler {

        private HttpRequest request;
        private HttpResponse response;

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {
            LOG.info("Received test message: " + request);
            context.setAttribute(AS2ServerManager.FROM, MDN_FROM);
            context.setAttribute(AS2ServerManager.SUBJECT, MDN_SUBJECT_PREFIX);

            this.request = request;
            this.response = response;
        }

        public HttpRequest getRequest() {
            return request;
        }

        public HttpResponse getResponse() {
            return response;
        }
    }

    private Triple<HttpEntity, HttpRequest, HttpResponse> executeRequest(Map<String, Object> headers) throws Exception {
        HttpEntity responseEntity = requestBodyAndHeaders("direct://SEND", EDI_MESSAGE, headers);

        return new ImmutableTriple(responseEntity, requestHandler.getRequest(), requestHandler.getResponse());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                Processor proc = new Processor() {
                    public void process(org.apache.camel.Exchange exchange) {
                        HttpMessage message = exchange.getIn(HttpMessage.class);
                        @SuppressWarnings("unused")
                        String body = message.getBody(String.class);
                    }
                };
                // test route for send
                from("direct://SEND").to("as2://" + PATH_PREFIX + "/send?inBody=ediMessage");
                
                from("jetty:http://localhost:" + MDN_TARGET_PORT + "/handle-receipts").process(proc);

            }
        };
    }

    private static void setupServerKeysAndCertificates() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        //
        // set up our certificates
        //
        KeyPairGenerator    kpg  = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        KeyPair issueKP = kpg.generateKeyPair();
        X509Certificate issueCert = Utils.makeCertificate(
                                        issueKP, issueDN, issueKP, issueDN);

        //
        // certificate we sign against
        //
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        serverSigningKP = kpg.generateKeyPair();
        X509Certificate signingCert = Utils.makeCertificate(
                serverSigningKP, signingDN, issueKP, issueDN);

        serverCertList = new ArrayList<>();

        serverCertList.add(signingCert);
        serverCertList.add(issueCert);
    }

    private static void receiveTestMessages() throws IOException {
        serverConnection = new AS2ServerConnection(AS2_VERSION, ORIGIN_SERVER_NAME,
                SERVER_FQDN, PARTNER_TARGET_PORT, AS2SignatureAlgorithm.SHA256WITHRSA, serverCertList.toArray(new Certificate[0]), serverSigningKP.getPrivate(), serverSigningKP.getPrivate());
        requestHandler = new  RequestHandler();
        serverConnection.listen("/", requestHandler);
    }

    private void setupKeysAndCertificates() throws Exception {
        //
        // set up our certificates
        //
        KeyPairGenerator    kpg  = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();
        issueCert = Utils.makeCertificate(
                                        issueKP, issueDN, issueKP, issueDN);

        //
        // certificate we sign against
        //
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(
                                        signingKP, signingDN, issueKP, issueDN);

        certList = new ArrayList<>();

        certList.add(signingCert);
        certList.add(issueCert);

        // keys used to encrypt/decrypt
        decryptingKP = signingKP;
    }
}
