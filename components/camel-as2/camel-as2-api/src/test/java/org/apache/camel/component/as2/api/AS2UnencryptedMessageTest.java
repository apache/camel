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
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AS2UnencryptedMessageTest extends AS2MessageTestBase {

    @BeforeAll
    public static void setUpOnce() throws Exception {
        setupKeysAndCertificates();

        testServer = new AS2ServerConnection(
                AS2_VERSION, "MyServer-HTTP/1.1", SERVER_FQDN, TARGET_PORT, AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), null, MDN_MESSAGE_TEMPLATE,
                null, null);
        testServer.listen("*", new HttpRequestHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                    throws HttpException, IOException {
                try {
                    org.apache.camel.component.as2.api.entity.EntityParser.parseAS2MessageEntity(request);
                    context.setAttribute(AS2ServerManager.SUBJECT, SUBJECT);
                    context.setAttribute(AS2ServerManager.FROM, AS2_NAME);
                    ediEntity = HttpMessageUtils.extractEdiPayload(request, new HttpMessageUtils.DecrpytingAndSigningInfo(
                            testServer.getValidateSigningCertificateChain(), testServer.getDecryptingPrivateKey()));
                } catch (Exception e) {
                    throw new HttpException("Failed to parse AS2 Message Entity", e);
                }
            }
        });
    }

    @Test
    public void plainEDIMessageRequestTest() throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII),
                null, null, null, null, null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null,
                "file.txt", null);

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
        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for message");

        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof ApplicationEDIFACTEntity, "Unexpected request entity type");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) entity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for entity");
        assertTrue(ediEntity.isMainBody(), "Entity not set as main body of request");
    }

    @Test
    public void compressedMessageRequestTest() throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN_COMPRESSED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                null, null, null, AS2CompressionAlgorithm.ZLIB,
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

        // Validated compessed part.
        MimeEntity compressedEntity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(compressedEntity instanceof ApplicationEDIFACTEntity, "Enveloped mime part incorrect type ");
        ApplicationEDIFACTEntity ediEntity = (ApplicationEDIFACTEntity) compressedEntity;
        assertTrue(ediEntity.getContentType().startsWith(AS2MediaType.APPLICATION_EDIFACT),
                "Unexpected content type for compressed entity");
        assertFalse(ediEntity.isMainBody(), "Compressed entity set as main body of request");

    }

    @Test
    public void mdnMessageTest() throws Exception {
        AS2ClientManager clientManager = createDefaultClientManager();

        HttpCoreContext httpContext = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII),
                null, null, null, null, null,
                DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null,
                "file.txt", null);

        HttpResponse response = httpContext.getResponse();
        assertEquals(HttpVersion.HTTP_1_1, response.getVersion(), "Unexpected method value");
        assertEquals(HttpStatus.SC_OK, response.getCode(), "Unexpected method value");
        assertEquals(EnglishReasonPhraseCatalog.INSTANCE.getReason(200, null), response.getReasonPhrase(),
                "Unexpected method value");

        assertTrue(response instanceof ClassicHttpResponse);
        HttpEntity responseEntity = ((ClassicHttpResponse) response).getEntity();
        assertNotNull(responseEntity, "Response entity");
        assertTrue(responseEntity instanceof MultipartSignedEntity, "Unexpected response entity type");
        MultipartSignedEntity responseSignedEntity = (MultipartSignedEntity) responseEntity;
        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity,
                "Signed entity wrong type");
        DispositionNotificationMultipartReportEntity reportEntity
                = (DispositionNotificationMultipartReportEntity) responseSignedDataEntity;
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, StandardCharsets.US_ASCII).toString(),
                firstPart.getContentType(),
                "Unexpected content type in first body part of report");
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION).toString(),
                secondPart.getContentType(),
                "Unexpected content type in second body part of report");
        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");

        // Validate Signature
        assertTrue(SigningUtils.isValid(responseSignedEntity, new Certificate[] { signingCert }), "Signature is invalid");
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,false", "false,false,true", "false,true,false", "false,true,true" })
    void unencryptedBinaryContentTransferEncodingTest(boolean encrypt, boolean sign, boolean compress) throws IOException {
        binaryContentTransferEncodingTest(encrypt, sign, compress);
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true" })
    void unencryptedCompressionSignatureOrderTest(boolean encrypt, boolean compressBeforeSign) throws IOException {
        compressionSignatureOrderTest(encrypt, compressBeforeSign);
    }
}
