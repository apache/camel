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

import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
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
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AS2MessageTest {

    public static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
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
            + "UNZ+1+00000000000778'";

    private static final Logger LOG = LoggerFactory.getLogger(AS2MessageTest.class);

    private static final String METHOD = "POST";
    private static final String TARGET_HOST = "localhost";
    private static final int TARGET_PORT = AvailablePortFinder.getNextAvailable();
    private static final String RECIPIENT_DELIVERY_ADDRESS = "http://localhost:" + TARGET_PORT + "/handle-receipts";
    private static final String AS2_VERSION = "1.1";
    private static final String USER_AGENT = "Camel AS2 Endpoint";
    private static final String REQUEST_URI = "/";
    private static final String AS2_NAME = "878051556";
    private static final String SUBJECT = "Test Case";
    private static final String FROM = "mrAS@example.org";
    private static final String CLIENT_FQDN = "client.example.org";
    private static final String SERVER_FQDN = "server.example.org";
    private static final String REPORTING_UA = "Server Responding with MDN";
    private static final String DISPOSITION_NOTIFICATION_TO = "mrAS@example.org";
    private static final String DISPOSITION_NOTIFICATION_OPTIONS = "signed-receipt-protocol=optional,pkcs7-signature; signed-receipt-micalg=optional,sha1";
    private static final String[] SIGNED_RECEIPT_MIC_ALGORITHMS = new String[] {"sha1", "md5"};

    private static final HttpDateGenerator DATE_GENERATOR = new HttpDateGenerator();

    private static AS2ServerConnection testServer;
    private static KeyPair issueKP;
    private static X509Certificate issueCert;

    private static KeyPair signingKP;
    private static X509Certificate signingCert;
    private static List<X509Certificate> certList;

    private AS2SignedDataGenerator gen;

    @BeforeAll
    public static void setUpOnce() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        //
        // set up our certificates
        //
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();
        issueCert = Utils.makeCertificate(issueKP, issueDN, issueKP, issueDN);

        //
        // certificate we sign against
        //
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(signingKP, signingDN, issueKP, issueDN);

        certList = new ArrayList<>();

        certList.add(signingCert);
        certList.add(issueCert);
        
        KeyPair decryptingKP = signingKP;

        testServer = new AS2ServerConnection(AS2_VERSION, "MyServer-HTTP/1.1", SERVER_FQDN, TARGET_PORT, AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), decryptingKP.getPrivate());
        testServer.listen("*", new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws HttpException, IOException {
                try {
                    org.apache.camel.component.as2.api.entity.EntityParser.parseAS2MessageEntity(request);
                    context.setAttribute(AS2ServerManager.SUBJECT, SUBJECT);
                    context.setAttribute(AS2ServerManager.FROM, AS2_NAME);
                    LOG.debug(AS2Utils.printMessage(request));
                } catch (Exception e) {
                    throw new HttpException("Failed to parse AS2 Message Entity", e);
                }
            }
        });
    }

    @AfterAll
    public static void tearDownOnce() throws Exception {
        testServer.close();
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
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(new IssuerAndSerialNumber(
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

    @Test
    public void plainEDIMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, null, null, null, null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationEDIFACTEntity, "Unexpected request entity type");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for entity");
        assertTrue(ediEntity.isMainBody(), "Entity not set as main body of request");
    }

    @Test
    public void multipartSignedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.MULTIPART_SIGNED), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity signedEntity = (MultipartSignedEntity) entity;
        assertTrue(signedEntity.isMainBody(), "Entity not set as main body of request");
        assertTrue(signedEntity.getPartCount() == 2, "Request contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(signedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) signedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(signedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) signedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE), "Unexpected content type for second mime part");
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
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(), null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, encryptionAlgorithm,
                certList.toArray(new Certificate[0]));

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) encryptedEntity;
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for enveloped mime part");
        assertFalse(ediEntity.isMainBody(), "Enveloped mime type set as main body of request");
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediEntity.getEdiMessage().replaceAll("[\n\r]", ""), "Unexpected content for enveloped mime part");
    }

    @Test
    public void aes128CbcEnvelopedAndSignedMessageTest() throws Exception {
        envelopedAndSignedMessageTest(AS2EncryptionAlgorithm.AES128_CBC);
    }

    public void envelopedAndSignedMessageTest(AS2EncryptionAlgorithm encryptionAlgorithm) throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED_ENCRYPTED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(), null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, encryptionAlgorithm,
                certList.toArray(new Certificate[0]));

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) encryptedEntity;
        assertTrue(multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED), "Unexpected content type for enveloped mime part");
        assertFalse(multipartSignedEntity.isMainBody(), "Enveloped mime type set as main body of request");
        assertTrue(multipartSignedEntity.getPartCount() == 2, "Request contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE), "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");

    }

    @Test
    public void signatureVerificationTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null);

        HttpRequest request = httpContext.getRequest();
        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
        MimeEntity signedEntity = multipartSignedEntity.getSignedDataEntity();
        assertTrue(signedEntity instanceof ApplicationEDIEntity, "Signed entity wrong type");
        ApplicationEDIEntity ediMessageEntity = (ApplicationEDIEntity) signedEntity;
        assertNotNull(ediMessageEntity, "Multipart signed entity does not contain EDI message entity");
        ApplicationPkcs7SignatureEntity signatureEntity = multipartSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Multipart signed entity does not contain signature entity");

        // Validate Signature
        assertTrue(multipartSignedEntity.isValid(), "Signature is invalid");

    }

    @Test
    public void mdnMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, null, null, null, null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null);

        HttpResponse response = httpContext.getResponse();
        assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion(), "Unexpected method value");
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode(), "Unexpected method value");
        assertEquals(EnglishReasonPhraseCatalog.INSTANCE.getReason(200, null), response.getStatusLine().getReasonPhrase(), "Unexpected method value");

        HttpEntity responseEntity = response.getEntity();
        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof MultipartSignedEntity, "Unexpected response entity type");
        MultipartSignedEntity responseSignedEntity = (MultipartSignedEntity)responseEntity;
        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity, "Signed entity wrong type");
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseSignedDataEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue(),
                     "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(), secondPart.getContentTypeValue(),
                     "Unexpected content type in second body part of report");
        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");

        // Validate Signature
        assertTrue(responseSignedEntity.isValid(), "Signature is invalid");
    }

    @Test
    public void asynchronousMdnMessageTest() throws Exception {

        AS2AsynchronousMDNManager mdnManager = new AS2AsynchronousMDNManager(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
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
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, null, "boundary", true,
                null, "Got ya message!");

        // Send MDN
        HttpCoreContext httpContext = mdnManager.send(mdn, RECIPIENT_DELIVERY_ADDRESS);
        HttpRequest mndRequest = httpContext.getRequest();
        DispositionNotificationMultipartReportEntity reportEntity = HttpMessageUtils.getEntity(mndRequest, DispositionNotificationMultipartReportEntity.class);
        assertNotNull(reportEntity, "Request does not contain resport");
        assertEquals(2, reportEntity.getPartCount(), "Report entity contains invalid number of parts");
        assertTrue(reportEntity.getPart(0) instanceof TextPlainEntity, "Report first part is not text entity");
        assertTrue(reportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity, "Report second part is not MDN entity");
        AS2MessageDispositionNotificationEntity mdnEntity = (AS2MessageDispositionNotificationEntity)reportEntity.getPart(1);
        assertEquals(REPORTING_UA, mdnEntity.getReportingUA(), "Unexpected value for Reporting UA");
        assertEquals(AS2_NAME, mdnEntity.getFinalRecipient(), "Unexpected value for Final Recipient");
        assertEquals(originalMessageId, mdnEntity.getOriginalMessageId(), "Unexpected value for Original Message ID");
        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY, mdnEntity.getDispositionMode(), "Unexpected value for Disposition Mode");
        assertEquals(AS2DispositionType.PROCESSED, mdnEntity.getDispositionType(), "Unexpected value for Disposition Type");
        assertEquals(dispositionModifier, mdnEntity.getDispositionModifier(), "Unexpected value for Disposition Modifier");
        assertArrayEquals(failureFields, mdnEntity.getFailureFields(), "Unexpected value for Failure Fields");
        assertArrayEquals(errorFields, mdnEntity.getErrorFields(), "Unexpected value for Error Fields");
        assertArrayEquals(warningFields, mdnEntity.getWarningFields(), "Unexpected value for Warning Fields");
        assertEquals(extensionFields, mdnEntity.getExtensionFields(), "Unexpected value for Extension Fields");
        ReceivedContentMic expectedMic = MicUtils.createReceivedContentMic(request, null);
        ReceivedContentMic mdnMic = mdnEntity.getReceivedContentMic();
        assertEquals(expectedMic.getEncodedMessageDigest(), mdnMic.getEncodedMessageDigest(), "Unexpected value for Recieved Content Mic");
        LOG.debug("\r\n" + AS2Utils.printMessage(mndRequest));
    }
    
    @Test
    public void compressedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), null,
                null, null, null, AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null,
                null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity)entity;
        assertTrue(compressedDataEntity.isMainBody(), "Entity not set as main body of request");

        // Validated compessed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity)compressedEntity;
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for compressed entity");
        assertFalse(ediEntity.isMainBody(), "Compressed entity set as main body of request");

    }


    @Test
    public void compressedAndSignedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), "base64",
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(), AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null,
                null);

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity)entity;
        assertTrue(compressedDataEntity.isMainBody(), "Entity not set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity)compressedEntity;
        assertTrue(multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED), "Unexpected content type for compressed entity");
        assertFalse(multipartSignedEntity.isMainBody(), "Multipart signed entity set as main body of request");
        assertTrue(multipartSignedEntity.getPartCount() == 2, "Multipart signed entity contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity)multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity)multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE), "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");
    }

    @Test
    public void envelopedAndCompressedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.ENCRYPTED_COMPRESSED,
                                                         ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), "base64", null, null, null,
                                                         AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                                                         AS2EncryptionAlgorithm.AES128_CBC, certList.toArray(new Certificate[0]));

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity)entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Enveloped mime part incorrect type ");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity)encryptedEntity;
        assertTrue(compressedDataEntity.getContentType().getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for compressed mime part");
        assertFalse(compressedDataEntity.isMainBody(), "Enveloped mime type set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity)compressedEntity;
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for compressed entity");
        assertFalse(ediEntity.isMainBody(), "Compressed entity set as main body of request");
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), ediEntity.getEdiMessage().replaceAll("[\n\r]", ""), "Unexpected content for enveloped mime part");
    }

    @Test
    public void envelopedCompressedAndSignedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_COMPRESSED_SIGNED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                AS2EncryptionAlgorithm.AES128_CBC, certList.toArray(new Certificate[0]));

        HttpRequest request = httpContext.getRequest();
        assertEquals(METHOD, request.getRequestLine().getMethod(), "Unexpected method value");
        assertEquals(REQUEST_URI, request.getRequestLine().getUri(), "Unexpected request URI value");
        assertEquals(HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion(), "Unexpected HTTP version value");

        assertEquals(SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue(), "Unexpected subject value");
        assertEquals(FROM, request.getFirstHeader(AS2Header.FROM).getValue(), "Unexpected from value");
        assertEquals(AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue(), "Unexpected AS2 version value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue(), "Unexpected AS2 from value");
        assertEquals(AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue(), "Unexpected AS2 to value");
        assertTrue(request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"), "Unexpected message id value");
        assertEquals(TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue(), "Unexpected target host value");
        assertEquals(USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue(), "Unexpected user agent value");
        assertNotNull(request.getFirstHeader(AS2Header.DATE), "Date value missing");
        assertNotNull(request.getFirstHeader(AS2Header.CONTENT_LENGTH), "Content length value missing");
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for message");

        assertTrue(request instanceof BasicHttpEntityEnclosingRequest, "Request does not contain entity");
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity, "Unexpected request entity type");
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity)entity;
        assertTrue(envelopedEntity.isMainBody(), "Entity not set as main body of request");

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue(encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity, "Enveloped mime part incorrect type ");
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity)encryptedEntity;
        assertTrue(compressedDataEntity.getContentType().getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME), "Unexpected content type for compressed mime part");
        assertFalse(compressedDataEntity.isMainBody(), "Enveloped mime type set as main body of request");

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity)compressedEntity;
        assertTrue(multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED), "Unexpected content type for compressed entity");
        assertFalse(multipartSignedEntity.isMainBody(), "Multipart signed entity set as main body of request");
        assertTrue(multipartSignedEntity.getPartCount() == 2, "Multipart signed entity contains invalid number of mime parts");

        // Validated first mime part.
        assertTrue(multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity, "First mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity)multipartSignedEntity.getPart(0);
        assertTrue(ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT), "Unexpected content type for first mime part");
        assertFalse(ediEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity)multipartSignedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE), "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");
    }

}
