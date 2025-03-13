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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.ApplicationXMLEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.bouncycastle.util.io.Streams;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.as2.api.AS2ServerManager} APIs that uses unencrypted AS2 message
 * types.
 */
public class AS2ServerManagerIT extends AS2ServerManagerITBase {

    protected static final Logger LOG = LoggerFactory.getLogger(AS2ServerManagerIT.class);

    @Test
    public void receivePlainEDIMessageTest() throws Exception {
        receivePlainEDIMessage(EDI_MESSAGE, null);
    }

    @Test
    public void receivePlainEDIStreamMessageTest() throws Exception {
        receivePlainEDIMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), null);
    }

    @Test
    public void receivePlainEDIBase64StreamMessageTest() throws Exception {
        receivePlainEDIMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), "base64");
    }

    private void receivePlainEDIMessage(Object msg, String encoding) throws Exception {
        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.PLAIN,
                AS2MediaType.APPLICATION_EDIFACT, null, encoding, null, null, null,
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");
        LOG.debug("poll result: {}", exchanges);

        Exchange exchange = exchanges.get(0);
        Message message = exchange.getIn();
        assertNotNull(message, "exchange message");

        HttpCoreContext coreContext = exchange.getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");
        HttpRequest request = coreContext.getRequest();
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

        ApplicationEntity appEntity = (ApplicationEntity) entity;
        if (encoding == null) {
            assertTrue(appEntity.getEdiMessage() instanceof String);
            String rcvdMessage = ((String) appEntity.getEdiMessage()).replaceAll("\r", "");
            assertEquals(EDI_MESSAGE, rcvdMessage, "EDI message does not match");
        } else if ("base64".equals(encoding)) {
            assertTrue(appEntity.getEdiMessage() instanceof InputStream);
            InputStream is = (InputStream) appEntity.getEdiMessage();
            String rcvdMessage = new String(is.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\r", "");
            assertEquals(EDI_MESSAGE, rcvdMessage, "EDI message does not match");
        }
        String rcvdMessageFromBody = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessageFromBody.replaceAll("[\n\r]", ""),
                "EDI message does not match");
    }

    private static AS2ClientConnection getAs2ClientConnection() throws IOException {
        AS2ClientConnection clientConnection
                = new AS2ClientConnection(
                        AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT, HTTP_SOCKET_TIMEOUT,
                        HTTP_CONNECTION_TIMEOUT, HTTP_CONNECTION_POOL_SIZE, HTTP_CONNECTION_POOL_TTL, clientSslContext,
                        null);
        return clientConnection;
    }

    @Test
    public void receiveMultipartSignedMessageTest() throws Exception {
        receiveMultipartSignedMessage(EDI_MESSAGE, null);
    }

    @Test
    public void receiveMultipartSignedStreamMessageTest() throws Exception {
        receiveMultipartSignedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), null);
    }

    @Test
    public void receiveMultipartSignedBase64StreamMessageTest() throws Exception {
        receiveMultipartSignedMessage(new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), "base64");
    }

    private void receiveMultipartSignedMessage(Object msg, String encoding) throws Exception {

        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.SIGNED,
                AS2MediaType.APPLICATION_EDIFACT, null, encoding,
                AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), null, DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");
        LOG.debug("poll result: {}", exchanges);

        Exchange exchange = exchanges.get(0);
        Message message = exchange.getIn();
        assertNotNull(message, "exchange message");
        HttpCoreContext coreContext = exchange.getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");
        HttpRequest request = coreContext.getRequest();
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

        // Validate Signature
        assertTrue(SigningUtils.isValid(signedEntity, new Certificate[] { signingCert }), "Signature is invalid");

        String rcvdMessage = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessage.replaceAll("[\n\r]", ""),
                "Unexpected content for enveloped mime part");
    }

    @Test
    public void receiveMultipartSignedXMLMessageTest() throws Exception {

        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.SIGNED,
                AS2MediaType.APPLICATION_XML, null, null, // this line is the difference
                AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), null, DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");
        LOG.debug("poll result: {}", exchanges);

        Exchange exchange = exchanges.get(0);
        Message message = exchange.getIn();
        assertNotNull(message, "exchange message");
        HttpCoreContext coreContext = exchange.getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");
        HttpRequest request = coreContext.getRequest();
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
        assertTrue(signedEntity.getPart(0) instanceof ApplicationXMLEntity, "First mime part incorrect type ");
        ApplicationXMLEntity xmlEntity = (ApplicationXMLEntity) signedEntity.getPart(0);
        assertTrue(xmlEntity.getContentType().startsWith(AS2MediaType.APPLICATION_XML),
                "Unexpected content type for first mime part");
        assertFalse(xmlEntity.isMainBody(), "First mime type set as main body of request");

        // Validate second mime part.
        assertTrue(signedEntity.getPart(1) instanceof ApplicationPkcs7SignatureEntity, "Second mime part incorrect type ");
        ApplicationPkcs7SignatureEntity signatureEntity = (ApplicationPkcs7SignatureEntity) signedEntity.getPart(1);
        assertTrue(signatureEntity.getContentType().startsWith(AS2MediaType.APPLICATION_PKCS7_SIGNATURE),
                "Unexpected content type for second mime part");
        assertFalse(signatureEntity.isMainBody(), "First mime type set as main body of request");

        // Validate Signature
        assertTrue(SigningUtils.isValid(signedEntity, new Certificate[] { signingCert }), "Signature is invalid");

        String rcvdMessage = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessage.replaceAll("[\n\r]", ""),
                "Unexpected content for enveloped mime part");
    }

    @Test
    public void receiveMultipartInvalidSignedMessageTest() throws Exception {

        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());
        String hackerIssueDN = "O=Hackers Unlimited Ltd., C=US";
        var hackerIssueKP = kpg.generateKeyPair();
        var hackerissueCert = Utils.makeCertificate(
                hackerIssueKP, hackerIssueDN, hackerIssueKP, hackerIssueDN);
        String hackerSigningDN = "CN=John Doe, E=j.doe@sharklasers.com, O=Self Signed, C=US";
        var hackerSigningKP = kpg.generateKeyPair();
        var hackerSigningCert = Utils.makeCertificate(
                hackerSigningKP, hackerSigningDN, hackerIssueKP, hackerIssueDN);

        clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.SIGNED,
                AS2MediaType.APPLICATION_EDIFACT, null, null,
                AS2SignatureAlgorithm.SHA256WITHRSA,
                new Certificate[] { hackerSigningCert }, hackerSigningKP.getPrivate(), null, DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");
        LOG.debug("poll result: {}", exchanges);

        Exchange exchange = exchanges.get(0);
        Message message = exchange.getIn();
        assertNotNull(message, "exchange message");
        HttpCoreContext coreContext = exchange.getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");
        HttpRequest request = coreContext.getRequest();
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

        // Validate Signature
        assertFalse(SigningUtils.isValid(signedEntity, new Certificate[] { signingCert }), "Signature must be invalid");

        String rcvdMessage = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessage.replaceAll("[\n\r]", ""),
                "Unexpected content for enveloped mime part");
    }

    // Verify that the payload is compressed before being signed.
    @Test
    public void receiveMultipartCompressedAndSignedMessageTest() throws Exception {
        receiveMultipartCompressedAndSignedMessage(EDI_MESSAGE, null);
    }

    @Test
    public void receiveMultipartCompressedAndSignedStreamMessageTest() throws Exception {
        receiveMultipartCompressedAndSignedMessage(
                new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), null);
    }

    // base64 encoded stream input, compressed then signed
    @Test
    public void receiveMultipartCompressedAndSignedBase64StreamMessageTest() throws Exception {
        receiveMultipartCompressedAndSignedMessage(
                new ByteArrayInputStream(EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII)), "base64");
    }

    private void receiveMultipartCompressedAndSignedMessage(Object msg, String encoding) throws Exception {
        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(msg, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.COMPRESSED_SIGNED,
                AS2MediaType.APPLICATION_EDIFACT, null, encoding,
                AS2SignatureAlgorithm.SHA256WITHRSA,
                certList.toArray(new Certificate[0]), signingKP.getPrivate(), AS2CompressionAlgorithm.ZLIB,
                DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        verifyMock(mockEndpoint);

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        verifyExchanges(exchanges);

        HttpCoreContext coreContext = exchanges.get(0).getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");

        HttpRequest request = coreContext.getRequest();
        verifyRequest(request);

        assertTrue(request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for message");
        assertTrue(request instanceof ClassicHttpRequest, "Request does not contain entity");
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertNotNull(entity, "Request does not contain entity");
        assertTrue(entity instanceof MultipartSignedEntity, "Unexpected request entity type");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
        assertTrue(multipartSignedEntity.isMainBody(), "Entity not set as main body of request");

        verifyCompressedSignedEntity(multipartSignedEntity);
    }

    @Test
    public void sendEditMessageToFailingProcessorTest() throws Exception {
        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        HttpCoreContext context = clientManager.send(EDI_MESSAGE, "/process_error", SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN,
                AS2MediaType.APPLICATION_EDIFACT, null, null, null, null, null,
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(0);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        HttpResponse response = context.getResponse();
        assertTrue(response instanceof ClassicHttpResponse, "Request does not contain entity");
        HttpEntity responseEntity = ((ClassicHttpResponse) response).getEntity();
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode(),
                "Unexpected status code for response");
        String errorMessage = new String(Streams.readAll(responseEntity.getContent()));
        assertEquals(EXPECTED_EXCEPTION_MSG, errorMessage, "");
    }

    @Test
    public void checkMDNTest() throws Exception {
        final AS2ClientConnection clientConnection = getAs2ClientConnection();
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        //Testing MDN parameter defaults
        HttpCoreContext response
                = clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.PLAIN,
                        AS2MediaType.APPLICATION_EDIFACT, null, null, null, null, null,
                        null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        assertEquals(new AS2Configuration().getServer(), response.getResponse().getFirstHeader(AS2Header.FROM).getValue(),
                "Default value for From header not set");
        assertEquals("MDN Response To:" + SUBJECT, response.getResponse().getFirstHeader(AS2Header.SUBJECT).getValue(),
                "Default value for Subject header not set");

        //Testing MDN parameter overwrites
        response = clientManager.send(EDI_MESSAGE, REQUEST_URI + "mdnTest", SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN,
                AS2MediaType.APPLICATION_EDIFACT, null, null, null, null, null,
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, null, null, null, null);

        assertEquals("MdnTestFrom", response.getResponse().getFirstHeader(AS2Header.FROM).getValue(),
                "Configured value for From header not set");
        assertEquals("MdnTestSubjectPrefix" + SUBJECT, response.getResponse().getFirstHeader(AS2Header.SUBJECT).getValue(),
                "Configured value for Subject header not set");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.clientSslContext = setupClientContext(context);
        this.serverSslContext = setupClientContext(context);
        AS2Component as2Component = (AS2Component) context.getComponent("as2");
        AS2Configuration configuration = as2Component.getConfiguration();
        configuration.setSslContext(serverSslContext);
        return context;
    }
}
