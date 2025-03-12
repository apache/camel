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

import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.camel.component.as2.internal.AS2ApiCollection;
import org.apache.camel.component.as2.internal.AS2ServerManagerApiMethod;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AS2ServerManagerITBase extends AbstractAS2ITSupport {

    protected static final String PROCESSOR_EXCEPTION_MSG = "Processor Exception";
    protected static final String EXPECTED_EXCEPTION_MSG = "Failed to process AS2 message: " + PROCESSOR_EXCEPTION_MSG;
    protected static final String PATH_PREFIX
            = AS2ApiCollection.getCollection().getApiName(AS2ServerManagerApiMethod.class).getName();

    protected static final String METHOD = "POST";
    protected static final String TARGET_HOST = "localhost";
    protected static final int TARGET_PORT = 8888;
    protected static final Duration HTTP_SOCKET_TIMEOUT = Duration.ofSeconds(5);
    protected static final Duration HTTP_CONNECTION_TIMEOUT = Duration.ofSeconds(5);
    protected static final Integer HTTP_CONNECTION_POOL_SIZE = 5;
    protected static final Duration HTTP_CONNECTION_POOL_TTL = Duration.ofMinutes(15);
    protected static final String AS2_VERSION = "1.1";
    protected static final String USER_AGENT = "Camel AS2 Endpoint";
    protected static final String REQUEST_URI = "/";
    protected static final String AS2_NAME = "878051556";
    protected static final String SUBJECT = "Test Case";
    protected static final String FROM = "mrAS@example.org";
    protected static final String CLIENT_FQDN = "example.org";
    protected static final String DISPOSITION_NOTIFICATION_TO = "mrAS@example.org";
    protected static final String SIGNED_RECEIPT_MIC_ALGORITHMS = "sha1,md5";
    protected static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
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

    protected static AS2SignedDataGenerator gen;
    protected static KeyPair issueKP;
    protected static X509Certificate issueCert;
    protected static KeyPair signingKP;
    protected static X509Certificate signingCert;
    protected static List<X509Certificate> certList;
    protected static KeyPair decryptingKP;
    protected static SSLContext clientSslContext;
    protected static SSLContext serverSslContext;

    @BeforeAll
    public static void setup() throws Exception {
        setupSigningGenerator();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for listen
                from("as2://" + PATH_PREFIX + "/listen?requestUriPattern=/")
                        .to("mock:as2RcvMsgs");

                // test route processing exception
                Processor failingProcessor = new Processor() {
                    public void process(org.apache.camel.Exchange exchange) throws Exception {
                        throw new Exception(PROCESSOR_EXCEPTION_MSG);
                    }
                };
                from("as2://" + PATH_PREFIX + "/listen?requestUriPattern=/process_error")
                        .process(failingProcessor)
                        .to("mock:as2RcvMsgs");

                // test route for listen with custom MDN parameters
                from("as2://" + PATH_PREFIX
                     + "/listen?requestUriPattern=/mdnTest&from=MdnTestFrom&subject=MdnTestSubjectPrefix")
                        .to("mock:as2RcvMsgs");
            }
        };
    }

    protected static void setupSigningGenerator() throws Exception {
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
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(
                new IssuerAndSerialNumber(new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
        attributes.add(new SMIMECapabilitiesAttribute(capabilities));

        gen = SigningUtils.createSigningGenerator(AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new X509Certificate[0]),
                signingKP.getPrivate());
        gen.addCertificates(certs);
    }

    protected static void setupKeysAndCertificates() throws Exception {
        // set up our certificates
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();
        issueCert = Utils.makeCertificate(issueKP, issueDN, issueKP, issueDN);

        // certificate we sign against
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(signingKP, signingDN, issueKP, issueDN);

        certList = new ArrayList<>();
        certList.add(signingCert);
        certList.add(issueCert);
        decryptingKP = signingKP;
    }

    public SSLContext setupClientContext(CamelContext context) throws Exception {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("jsse/localhost.p12");
        truststoreParameters.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(truststoreParameters);

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(clientSSLTrustManagers);

        SSLContext sslContext = sslContextParameters.createSSLContext(context);
        return sslContext;
    }

    public SSLContext setupServerContext(CamelContext context) throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.p12");
        ksp.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextServerParameters scsp = new SSLContextServerParameters();

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);

        SSLContext sslContext = sslContextParameters.createSSLContext(context);
        return sslContext;
    }

    public void verifyMock(MockEndpoint mockEndpoint) throws InterruptedException {
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();
    }

    public void verifyExchanges(List<Exchange> exchanges) {
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");
        Exchange exchange = exchanges.get(0);
        Message message = exchange.getIn();
        assertNotNull(message, "exchange message");
        String rcvdMessage = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessage.replaceAll("[\n\r]", ""),
                "Unexpected content for enveloped mime part");
    }

    public void verifyRequest(HttpRequest request) throws URISyntaxException {
        assertNotNull(request, "request");
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

    public void verifyCompressedSignedEntity(MultipartSignedEntity multipartSignedEntity) throws HttpException {
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

    public void verifyEdiFactEntity(MimeEntity entity) {
        assertTrue(entity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for compressed entity");
    }
}
