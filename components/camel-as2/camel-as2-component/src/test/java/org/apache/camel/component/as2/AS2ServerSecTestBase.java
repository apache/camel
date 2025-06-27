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
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AS2ServerSecTestBase extends AbstractAS2ITSupport {

    protected static final String TARGET_HOST = "localhost";
    protected static final int TARGET_PORT = 8888;
    protected static final Duration HTTP_SOCKET_TIMEOUT = Duration.ofSeconds(5);
    protected static final Duration HTTP_CONNECTION_TIMEOUT = Duration.ofSeconds(5);
    protected static final Integer HTTP_CONNECTION_POOL_SIZE = 5;
    private static final Duration HTTP_CONNECTION_POOL_TTL = Duration.ofMinutes(15);
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
    protected static KeyPair issueKP;
    protected static KeyPair signingKP;
    protected static X509Certificate signingCert;
    protected static KeyPair decryptingKP;

    @BeforeAll
    public static void setup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        setupKeysAndCertificates();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for listen
                from("as2://server/listen?requestUriPattern=/")
                        .to("mock:as2RcvMsgs");
            }
        };
    }

    protected void verifyOkResponse(HttpCoreContext context) {
        HttpResponse response = context.getAttribute(AS2ClientManager.HTTP_RESPONSE, HttpResponse.class);
        assertEquals(200, response.getCode());
    }

    protected void verifyMdnErrorDisposition(HttpCoreContext context, AS2DispositionModifier expectedDisposition) {
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = getAs2MdnEntity(context);
        AS2DispositionModifier dispositionModifier = messageDispositionNotificationEntity.getDispositionModifier();

        assertNotNull(dispositionModifier);
        assertTrue(dispositionModifier.isError());
        assertEquals(expectedDisposition.getModifier(), dispositionModifier.getModifier());
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType());
    }

    protected void verifyMdnSuccessDisposition(HttpCoreContext context) throws HttpException {
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = getAs2MdnEntity(context);

        assertNull(messageDispositionNotificationEntity.getDispositionModifier());
        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType());
        verifyMic(messageDispositionNotificationEntity, context);
    }

    protected void verifyMic(
            AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity, HttpCoreContext context)
            throws HttpException {
        HttpRequest request = context.getAttribute(AS2ClientManager.HTTP_REQUEST, HttpRequest.class);
        MicUtils.ReceivedContentMic computedContentMic = createReceivedContentMic(request);
        MicUtils.ReceivedContentMic receivedContentMic = messageDispositionNotificationEntity.getReceivedContentMic();

        assertEquals(computedContentMic.getEncodedMessageDigest(), receivedContentMic.getEncodedMessageDigest());
    }

    protected MicUtils.ReceivedContentMic createReceivedContentMic(HttpRequest request) throws HttpException {
        return MicUtils.createReceivedContentMic((ClassicHttpRequest) request, null, null);
    }

    protected AS2MessageDispositionNotificationEntity getAs2MdnEntity(HttpCoreContext context) {
        HttpResponse response = context.getAttribute(AS2ClientManager.HTTP_RESPONSE, HttpResponse.class);

        assert (response instanceof ClassicHttpResponse);
        ClassicHttpResponse classicHttpResponse = (ClassicHttpResponse) response;
        HttpEntity entity = classicHttpResponse.getEntity();

        assert (entity instanceof DispositionNotificationMultipartReportEntity);
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity) entity;

        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity
                = (AS2MessageDispositionNotificationEntity) reportEntity.getPart(1);
        return messageDispositionNotificationEntity;
    }

    protected HttpCoreContext sendWithInvalidSignature(AS2MessageStructure structure) throws Exception {
        return generateInvalidCrypto((Certificate sc, KeyPair skp, Certificate ec) -> send(structure, new Certificate[] { sc },
                skp.getPrivate(), null));
    }

    protected HttpCoreContext sendWithInvalidEncryption(AS2MessageStructure structure) throws Exception {
        return generateInvalidCrypto(
                (Certificate sc, KeyPair skp, Certificate ec) -> send(structure, null, null, new Certificate[] { ec }));
    }

    private HttpCoreContext generateInvalidCrypto(TriFunction<Certificate, KeyPair, Certificate, HttpCoreContext> fn)
            throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());
        String invalidIssueDN = "O=Hackers Unlimited Ltd., C=US";
        var invalidIssueKP = kpg.generateKeyPair();
        var invalidissueCert = Utils.makeCertificate(invalidIssueKP, invalidIssueDN, invalidIssueKP, invalidIssueDN);

        String invalidDN = "CN=John Doe, E=j.doe@sharklasers.com, O=Self Signed, C=US";
        var invalidKP = kpg.generateKeyPair();
        var invalidCert = Utils.makeCertificate(invalidKP, invalidDN, invalidIssueKP, invalidIssueDN);
        return fn.apply(invalidCert, invalidKP, invalidCert);
    }

    protected HttpCoreContext send(AS2MessageStructure structure) throws Exception {
        return send(structure, null, null, null);
    }

    protected HttpCoreContext send(
            AS2MessageStructure structure, Certificate[] sc, PrivateKey spk, Certificate[] ec)
            throws Exception {

        Certificate[] signingCertificate = sc == null ? new Certificate[] { this.signingCert } : sc;
        PrivateKey signingPrivateKey = spk == null ? this.signingKP.getPrivate() : spk;
        Certificate[] encryptingCertificate = ec == null ? new Certificate[] { this.signingCert } : ec;

        AS2SignatureAlgorithm signingAlgorithm = structure.isSigned() ? AS2SignatureAlgorithm.SHA256WITHRSA : null;
        signingCertificate = structure.isSigned() ? signingCertificate : null;
        signingPrivateKey = structure.isSigned() ? signingPrivateKey : null;
        AS2EncryptionAlgorithm encryptionAlgorithm = structure.isEncrypted() ? AS2EncryptionAlgorithm.AES128_CBC : null;
        encryptingCertificate = structure.isEncrypted() ? encryptingCertificate : null;
        AS2CompressionAlgorithm compressionAlgorithm = structure.isCompressed() ? AS2CompressionAlgorithm.ZLIB : null;

        return clientConnection().send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                structure,
                AS2MediaType.APPLICATION_EDIFACT, null, null,
                signingAlgorithm, signingCertificate, signingPrivateKey, compressionAlgorithm,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, encryptionAlgorithm,
                encryptingCertificate, null, null, null, null, null);
    }

    protected AS2ClientManager clientConnection() throws IOException {
        AS2ClientConnection clientConnection
                = new AS2ClientConnection(
                        AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT, HTTP_SOCKET_TIMEOUT,
                        HTTP_CONNECTION_TIMEOUT, HTTP_CONNECTION_POOL_SIZE, HTTP_CONNECTION_POOL_TTL, null,
                        null);
        return new AS2ClientManager(clientConnection);
    }

    protected static void setupKeysAndCertificates() throws Exception {
        // set up our certificates
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();

        // certificate we sign against
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(signingKP, signingDN, issueKP, issueDN);
        decryptingKP = signingKP;
    }

    private interface TriFunction<T, U, V, R> {
        R apply(T var1, U var2, V var3) throws Exception;
    }
}
