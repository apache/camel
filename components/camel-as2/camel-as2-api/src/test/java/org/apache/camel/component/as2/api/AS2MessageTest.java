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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
    private static final int TARGET_PORT = AvailablePortFinder.getNextAvailable(8080);
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

    @BeforeClass
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

    @AfterClass
    public static void tearDownOnce() throws Exception {
        testServer.close();
    }

    @Before
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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue("Unexpected content type for entity",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertTrue("Entity not set as main body of request", ediEntity.isMainBody());
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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.MULTIPART_SIGNED));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof MultipartSignedEntity);
        MultipartSignedEntity signedEntity = (MultipartSignedEntity) entity;
        assertTrue("Entity not set as main body of request", signedEntity.isMainBody());
        assertTrue("Request contains invalid number of mime parts", signedEntity.getPartCount() == 2);

        // Validated first mime part.
        assertTrue("First mime part incorrect type ", signedEntity.getPart(0) instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) signedEntity.getPart(0);
        assertTrue("Unexpected content type for first mime part",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("First mime type set as main body of request", ediEntity.isMainBody());

        // Validate second mime part.
        assertTrue("Second mime part incorrect type ",
                signedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) signedEntity.getPart(1);
        assertTrue("Unexpected content type for second mime part",
                signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE));
        assertFalse("First mime type set as main body of request", signatureEntity.isMainBody());

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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity);
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue("Entity not set as main body of request", envelopedEntity.isMainBody());

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue("Enveloped mime part incorrect type ", encryptedEntity instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) encryptedEntity;
        assertTrue("Unexpected content type for enveloped mime part",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("Enveloped mime type set as main body of request", ediEntity.isMainBody());
        assertEquals("Unexpected content for enveloped mime part", EDI_MESSAGE.replaceAll("[\n\r]", ""),
                ediEntity.getEdiMessage().replaceAll("[\n\r]", ""));

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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity);
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue("Entity not set as main body of request", envelopedEntity.isMainBody());

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue("Enveloped mime part incorrect type ", encryptedEntity instanceof MultipartSignedEntity);
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) encryptedEntity;
        assertTrue("Unexpected content type for enveloped mime part",
                multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED));
        assertFalse("Enveloped mime type set as main body of request", multipartSignedEntity.isMainBody());
        assertTrue("Request contains invalid number of mime parts", multipartSignedEntity.getPartCount() == 2);

        // Validated first mime part.
        assertTrue("First mime part incorrect type ", multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue("Unexpected content type for first mime part",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("First mime type set as main body of request", ediEntity.isMainBody());

        // Validate second mime part.
        assertTrue("Second mime part incorrect type ",
                multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue("Unexpected content type for second mime part",
                signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE));
        assertFalse("First mime type set as main body of request", signatureEntity.isMainBody());

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
        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof MultipartSignedEntity);
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
        MimeEntity signedEntity = multipartSignedEntity.getSignedDataEntity();
        assertTrue("Signed entity wrong type", signedEntity instanceof ApplicationEDIEntity);
        ApplicationEDIEntity ediMessageEntity = (ApplicationEDIEntity) signedEntity;
        assertNotNull("Multipart signed entity does not contain EDI message entity", ediMessageEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = multipartSignedEntity.getSignatureEntity();
        assertNotNull("Multipart signed entity does not contain signature entity", signatureEntity);

        // Validate Signature
        assertTrue("Signature is invalid", multipartSignedEntity.isValid());

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
        assertEquals("Unexpected method value", HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        assertEquals("Unexpected method value", HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("Unexpected method value", EnglishReasonPhraseCatalog.INSTANCE.getReason(200, null),
                response.getStatusLine().getReasonPhrase());

        HttpEntity responseEntity = response.getEntity();
        assertNotNull("Response entity", responseEntity);
        assertTrue("Unexpected response entity type", responseEntity instanceof MultipartSignedEntity);
        MultipartSignedEntity responseSignedEntity = (MultipartSignedEntity) responseEntity;
        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue("Signed entity wrong type",
                responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity);
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity) responseSignedDataEntity;
        assertEquals("Unexpected number of body parts in report", 2, reportEntity.getPartCount());
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals("Unexpected content type in first body part of report",
                ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(),
                firstPart.getContentTypeValue());
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals("Unexpected content type in second body part of report",
                ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(),
                secondPart.getContentTypeValue());
        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull("Signature Entity", signatureEntity);

        // Validate Signature
        assertTrue("Signature is invalid", responseSignedEntity.isValid());
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
        DispositionNotificationMultipartReportEntity reportEntity = HttpMessageUtils.getEntity(mndRequest,
                DispositionNotificationMultipartReportEntity.class);
        assertNotNull("Request does not contain resport", reportEntity);
        assertEquals("Report entity contains invalid number of parts", 2, reportEntity.getPartCount());
        assertTrue("Report first part is not text entity", reportEntity.getPart(0) instanceof TextPlainEntity);
        assertTrue("Report second part is not MDN entity",
                reportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity);
        AS2MessageDispositionNotificationEntity mdnEntity = (AS2MessageDispositionNotificationEntity) reportEntity
                .getPart(1);
        assertEquals("Unexpected value for Reporting UA", REPORTING_UA, mdnEntity.getReportingUA());
        assertEquals("Unexpected value for Final Recipient", AS2_NAME, mdnEntity.getFinalRecipient());
        assertEquals("Unexpected value for Original Message ID", originalMessageId, mdnEntity.getOriginalMessageId());
        assertEquals("Unexpected value for Disposition Mode", DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                mdnEntity.getDispositionMode());
        assertEquals("Unexpected value for Disposition Type", AS2DispositionType.PROCESSED,
                mdnEntity.getDispositionType());
        assertEquals("Unexpected value for Disposition Modifier", dispositionModifier,
                mdnEntity.getDispositionModifier());
        assertArrayEquals("Unexpected value for Failure Fields", failureFields, mdnEntity.getFailureFields());
        assertArrayEquals("Unexpected value for Error Fields", errorFields, mdnEntity.getErrorFields());
        assertArrayEquals("Unexpected value for Warning Fields", warningFields, mdnEntity.getWarningFields());
        assertEquals("Unexpected value for Extension Fields", extensionFields, mdnEntity.getExtensionFields());
        ReceivedContentMic expectedMic = MicUtils.createReceivedContentMic(request, null);
        ReceivedContentMic mdnMic = mdnEntity.getReceivedContentMic();
        assertEquals("Unexpected value for Recieved Content Mic", expectedMic.getEncodedMessageDigest(),
                mdnMic.getEncodedMessageDigest());
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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeCompressedDataEntity);
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity) entity;
        assertTrue("Entity not set as main body of request", compressedDataEntity.isMainBody());

        // Validated compessed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue("Enveloped mime part incorrect type ", compressedEntity instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) compressedEntity;
        assertTrue("Unexpected content type for compressed entity",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("Compressed entity set as main body of request", ediEntity.isMainBody());

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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeCompressedDataEntity);
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity) entity;
        assertTrue("Entity not set as main body of request", compressedDataEntity.isMainBody());

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue("Enveloped mime part incorrect type ", compressedEntity instanceof MultipartSignedEntity);
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) compressedEntity;
        assertTrue("Unexpected content type for compressed entity",
                multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED));
        assertFalse("Multipart signed entity set as main body of request", multipartSignedEntity.isMainBody());
        assertTrue("Multipart signed entity contains invalid number of mime parts",
                multipartSignedEntity.getPartCount() == 2);

        // Validated first mime part.
        assertTrue("First mime part incorrect type ", multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue("Unexpected content type for first mime part",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("First mime type set as main body of request", ediEntity.isMainBody());
        
        // Validate second mime part.
        assertTrue("Second mime part incorrect type ",
                multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue("Unexpected content type for second mime part",
                signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE));
        assertFalse("First mime type set as main body of request", signatureEntity.isMainBody());
    }

    @Test
    public void envelopedAndCompressedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        LOG.info("Key Algoritm: " + signingKP.getPrivate().getAlgorithm());

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII), "base64",
                null, null, null, AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, AS2EncryptionAlgorithm.AES128_CBC,
                certList.toArray(new Certificate[0]));

        HttpRequest request = httpContext.getRequest();
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity);
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue("Entity not set as main body of request", envelopedEntity.isMainBody());

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue("Enveloped mime part incorrect type ", encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity);
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity) encryptedEntity;
        assertTrue("Unexpected content type for compressed mime part",
                compressedDataEntity.getContentType().getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));
        assertFalse("Enveloped mime type set as main body of request", compressedDataEntity.isMainBody());

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue("Enveloped mime part incorrect type ", compressedEntity instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) compressedEntity;
        assertTrue("Unexpected content type for compressed entity",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("Compressed entity set as main body of request", ediEntity.isMainBody());
        assertEquals("Unexpected content for enveloped mime part", EDI_MESSAGE.replaceAll("[\n\r]", ""),
                ediEntity.getEdiMessage().replaceAll("[\n\r]", ""));
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
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1,
                request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION,
                request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value",
                request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT,
                request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT,
                request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message",
                request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationPkcs7MimeEnvelopedDataEntity);
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity = (ApplicationPkcs7MimeEnvelopedDataEntity) entity;
        assertTrue("Entity not set as main body of request", envelopedEntity.isMainBody());

        // Validated enveloped part.
        MimeEntity encryptedEntity = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());
        assertTrue("Enveloped mime part incorrect type ", encryptedEntity instanceof ApplicationPkcs7MimeCompressedDataEntity);
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity = (ApplicationPkcs7MimeCompressedDataEntity) encryptedEntity;
        assertTrue("Unexpected content type for compressed mime part",
                compressedDataEntity.getContentType().getValue().startsWith(AS2MimeType.APPLICATION_PKCS7_MIME));
        assertFalse("Enveloped mime type set as main body of request", compressedDataEntity.isMainBody());

        // Validated compressed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue("Enveloped mime part incorrect type ", compressedEntity instanceof MultipartSignedEntity);
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) compressedEntity;
        assertTrue("Unexpected content type for compressed entity",
                multipartSignedEntity.getContentType().getValue().startsWith(AS2MediaType.MULTIPART_SIGNED));
        assertFalse("Multipart signed entity set as main body of request", multipartSignedEntity.isMainBody());
        assertTrue("Multipart signed entity contains invalid number of mime parts",
                multipartSignedEntity.getPartCount() == 2);

        // Validated first mime part.
        assertTrue("First mime part incorrect type ", multipartSignedEntity.getPart(0) instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) multipartSignedEntity.getPart(0);
        assertTrue("Unexpected content type for first mime part",
                ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("First mime type set as main body of request", ediEntity.isMainBody());
        
        // Validate second mime part.
        assertTrue("Second mime part incorrect type ",
                multipartSignedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) multipartSignedEntity.getPart(1);
        assertTrue("Unexpected content type for second mime part",
                signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE));
        assertFalse("First mime type set as main body of request", signatureEntity.isMainBody());

    }

}
