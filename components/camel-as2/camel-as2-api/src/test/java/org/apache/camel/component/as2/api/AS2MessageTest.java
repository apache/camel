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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AS2MessageTest.class);

    private static final String METHOD = "POST";
    private static final String TARGET_HOST = "localhost";
    private static final int TARGET_PORT = 8080;
    private static final String AS2_VERSION = "1.1";
    private static final String USER_AGENT = "Camel AS2 Endpoint";
    private static final String REQUEST_URI = "/";
    private static final String AS2_NAME = "878051556";
    private static final String SUBJECT = "Test Case";
    private static final String FROM = "mrAS@example.org";
    private static final String CLIENT_FQDN = "client.example.org";
    private static final String SERVER_FQDN = "server.example.org";
    private static final String DISPOSITION_NOTIFICATION_TO = "mrAS@example.org";
    private static final String[] SIGNED_RECEIPT_MIC_ALGORITHMS = new String[] {"sha1", "md5"};
    

    private static AS2ServerConnection testServer;

    private AS2SignedDataGenerator gen;
    
    private KeyPair issueKP;
    private X509Certificate issueCert;

    private KeyPair signingKP;
    private X509Certificate signingCert;
    private List<X509Certificate> certList;

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
        
        certList = new ArrayList<X509Certificate>();

        certList.add(signingCert);
        certList.add(issueCert);

    }
    
    @BeforeClass
    public static void setUpOnce() throws Exception {
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
        KeyPair signingKP = kpg.generateKeyPair();
        X509Certificate signingCert = Utils.makeCertificate(
                                        signingKP, signingDN, issueKP, issueDN);
        
        List<X509Certificate> certList = new ArrayList<X509Certificate>();

        certList.add(signingCert);
        certList.add(issueCert);

        
        testServer = new AS2ServerConnection(AS2_VERSION, "MyServer-HTTP/1.1", SERVER_FQDN, 8080, certList.toArray(new Certificate[0]), signingKP.getPrivate());
        testServer.listen("*", new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws HttpException, IOException {
                try {
                    org.apache.camel.component.as2.api.entity.EntityParser.parseAS2MessageEntity(request);
                    context.setAttribute(SUBJECT, SUBJECT);
                    context.setAttribute(FROM, AS2_NAME);
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
    public void plainEDIMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, null, null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS);
        
        HttpRequest request = httpContext.getRequest();
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion());

        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value", request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message", request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));

        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue("Unexpected content type for entity", ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertTrue("Entity not set as main body of request", ediEntity.isMainBody());
    }

    @Test
    public void multipartSignedMessageTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, certList.toArray(new Certificate[0]), signingKP.getPrivate(), DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS);
        
        HttpRequest request = httpContext.getRequest();
        assertEquals("Unexpected method value", METHOD, request.getRequestLine().getMethod());
        assertEquals("Unexpected request URI value", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals("Unexpected HTTP version value", HttpVersion.HTTP_1_1, request.getRequestLine().getProtocolVersion());
        
        assertEquals("Unexpected subject value", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertEquals("Unexpected from value", FROM, request.getFirstHeader(AS2Header.FROM).getValue());
        assertEquals("Unexpected AS2 version value", AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertEquals("Unexpected AS2 from value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals("Unexpected AS2 to value", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertTrue("Unexpected message id value", request.getFirstHeader(AS2Header.MESSAGE_ID).getValue().endsWith(CLIENT_FQDN + ">"));
        assertEquals("Unexpected target host value", TARGET_HOST + ":" + TARGET_PORT, request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals("Unexpected user agent value", USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull("Date value missing", request.getFirstHeader(AS2Header.DATE));
        assertNotNull("Content length value missing", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertTrue("Unexpected content type for message", request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.MULTIPART_SIGNED));
        
        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof MultipartSignedEntity);
        MultipartSignedEntity signedEntity = (MultipartSignedEntity)entity;
        assertTrue("Entity not set as main body of request", signedEntity.isMainBody());
        assertTrue("Request contains invalid number of mime parts", signedEntity.getPartCount() == 2);
        
        // Validated first mime part.
        assertTrue("First mime part incorrect type ", signedEntity.getPart(0) instanceof ApplicationEDIFACTEntity);
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) signedEntity.getPart(0);
        assertTrue("Unexpected content type for first mime part", ediEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertFalse("First mime type set as main body of request", ediEntity.isMainBody());
        
        // Validate second mime part.
        assertTrue("Second mime part incorrect type ", signedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity);
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) signedEntity.getPart(1);
        assertTrue("Unexpected content type for second mime part", signatureEntity.getContentType().getValue().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE));
        assertFalse("First mime type set as main body of request", signatureEntity.isMainBody());
        
    }
    
    @Test
    public void signatureVerificationTest() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.SIGNED, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, certList.toArray(new Certificate[0]), signingKP.getPrivate(), DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS);
        
        HttpRequest request = httpContext.getRequest();
        assertTrue("Request does not contain entity", request instanceof BasicHttpEntityEnclosingRequest);
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest)request).getEntity();
        assertNotNull("Request does not contain entity", entity);
        assertTrue("Unexpected request entity type", entity instanceof MultipartSignedEntity);
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity)entity;
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
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII),
                null, null, null, DISPOSITION_NOTIFICATION_TO, null);
        
        @SuppressWarnings("unused")
        HttpResponse response = httpContext.getResponse();
    }
        
}
