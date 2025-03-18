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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.*;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7SignatureEntity;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link AS2AsyncMDNServerManager}.
 */
public class AS2AsyncMDNServerManagerIT extends AbstractAS2ITSupport {

    private static final String SERVER_FQDN = "server.example.com";
    private static final String ORIGIN_SERVER_NAME = "AS2ClientManagerIntegrationTest Server";
    private static final String AS2_VERSION = "1.1";
    private static final String REQUEST_URI = "/";
    private static final String SUBJECT = "Test Case";
    private static final String AS2_NAME = "878051556";
    private static final String FROM = "mrAS@example.org";
    private static final String MDN_FROM = "as2Test@server.example.com";
    private static final String MDN_SUBJECT_PREFIX = "MDN Response:";
    private static final String MDN_MESSAGE_TEMPLATE = "TBD";
    private static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
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
    private static final int PARTNER_TARGET_PORT = 8888;

    private static final int RECEIPT_SERVER_PORT = AvailablePortFinder.getNextAvailable();
    private static final int RECEIPT_SERVER_PORT2 = AvailablePortFinder.getNextAvailable();
    private static final int RECEIPT_SERVER_PORT3 = AvailablePortFinder.getNextAvailable();
    private static final int RECEIPT_SERVER_PORT4 = AvailablePortFinder.getNextAvailable();

    private static AS2ServerConnection serverConnection;
    private static RequestHandler requestHandler;
    private static final String SIGNED_RECEIPT_MIC_ALGORITHMS = "sha1,md5";
    private static KeyPair serverKP;
    private static X509Certificate serverCert;
    private static KeyPair clientKeyPair;
    private static X509Certificate clientCert;

    @BeforeAll
    public static void setupTest() throws Exception {
        setupKeysAndCertificates();
        receiveTestMessages();
    }

    @AfterAll
    public static void teardownTest() {
        if (serverConnection != null) {
            serverConnection.close();
        }
    }

    // Verify the MDN is receipt returned asynchronously from the server when the request headers includes the
    // 'Receipt-Delivery-Option' header specifying the return-URL.
    @Test
    public void deliveryHeaderMultipartReportTest() throws Exception {
        DispositionNotificationMultipartReportEntity reportEntity
                = executeRequestWithAsyncResponseHeader("direct://SEND", RECEIPT_SERVER_PORT, "mock:as2RcvRcptMsgs");
        verifyMultiPartReportParts(reportEntity);
        verifyMultiPartReportEntity(reportEntity);
    }

    // Verify the MDN is receipt returned asynchronously from the server when the endpoint uri includes the
    // 'Receipt-Delivery-Option' path variable specifying the return-URL.
    @Test
    public void deliveryPathMultipartReportTest() throws Exception {
        DispositionNotificationMultipartReportEntity reportEntity
                = executeRequestWithAsyncResponsePath("direct://SEND3", "mock:as2RcvRcptMsgs3");
        verifyMultiPartReportParts(reportEntity);
        verifyMultiPartReportEntity(reportEntity);
    }

    // Verify the signed MDN receipt returned asynchronously from the server when the request headers includes the
    // 'Receipt-Delivery-Option' header specifying the return-URL.
    @Test
    public void deliveryHeaderMultipartSignedEntityTest() throws Exception {
        MultipartSignedEntity responseSignedEntity
                = executeRequestWithSignedAsyncResponseHeader("direct://SEND", RECEIPT_SERVER_PORT2, "mock:as2RcvRcptMsgs2");

        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity,
                "Signed entity wrong type");

        DispositionNotificationMultipartReportEntity reportEntity
                = (DispositionNotificationMultipartReportEntity) responseSignedEntity.getSignedDataEntity();
        verifyMultiPartReportParts(reportEntity);
        verifyMultiPartReportEntity(reportEntity);

        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");
        verifyMdnSignature(reportEntity);
    }

    // Verify the signed MDN receipt returned asynchronously from the server when the endpoint uri includes the
    // 'Receipt-Delivery-Option' path variable specifying the return-URL.
    @Test
    public void deliveryPathMultipartSignedEntityTest() throws Exception {
        MultipartSignedEntity responseSignedEntity
                = executeRequestWithSignedAsyncResponsePath("direct://SEND4", "mock:as2RcvRcptMsgs4");

        MimeEntity responseSignedDataEntity = responseSignedEntity.getSignedDataEntity();
        assertTrue(responseSignedDataEntity instanceof DispositionNotificationMultipartReportEntity,
                "Signed entity wrong type");

        DispositionNotificationMultipartReportEntity reportEntity
                = (DispositionNotificationMultipartReportEntity) responseSignedEntity.getSignedDataEntity();
        verifyMultiPartReportParts(reportEntity);
        verifyMultiPartReportEntity(reportEntity);

        ApplicationPkcs7SignatureEntity signatureEntity = responseSignedEntity.getSignatureEntity();
        assertNotNull(signatureEntity, "Signature Entity");
        verifyMdnSignature(reportEntity);
    }

    private void verifyMultiPartReportParts(DispositionNotificationMultipartReportEntity reportEntity) {
        assertEquals(2, reportEntity.getPartCount(), "Unexpected number of body parts in report");

        MimeEntity reportPartOne = reportEntity.getPart(0);
        assertEquals(ContentType.create(AS2MimeType.TEXT_PLAIN, StandardCharsets.US_ASCII).toString(),
                reportPartOne.getContentType(), "Unexpected content type in first body part of report");

        MimeEntity reportPartTwo = reportEntity.getPart(1);
        assertEquals(ContentType.create(
                AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION).toString(), reportPartTwo.getContentType(),
                "Unexpected content type in second body part of report");

        assertTrue(reportPartTwo instanceof AS2MessageDispositionNotificationEntity);
    }

    private void verifyMultiPartReportEntity(DispositionNotificationMultipartReportEntity reportEntity) {
        // second part of MDN report
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity
                = (AS2MessageDispositionNotificationEntity) reportEntity.getPart(1);

        assertEquals(ORIGIN_SERVER_NAME, messageDispositionNotificationEntity.getReportingUA(),
                "Unexpected value for reporting UA");

        assertEquals(AS2_NAME, messageDispositionNotificationEntity.getFinalRecipient(),
                "Unexpected value for final recipient");

        String uniqueMessageId = HttpMessageUtils.getHeaderValue(requestHandler.getRequest(), AS2Header.MESSAGE_ID);
        assertEquals(uniqueMessageId, messageDispositionNotificationEntity.getOriginalMessageId(),
                "Unexpected value for original message ID");

        assertEquals(DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                messageDispositionNotificationEntity.getDispositionMode(), "Unexpected value for disposition mode");

        assertEquals(AS2DispositionType.PROCESSED, messageDispositionNotificationEntity.getDispositionType(),
                "Unexpected value for disposition type");
    }

    private void verifyMdnSignature(DispositionNotificationMultipartReportEntity reportEntity) throws HttpException {
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity
                = (AS2MessageDispositionNotificationEntity) reportEntity.getPart(1);

        MicUtils.ReceivedContentMic receivedContentMic = messageDispositionNotificationEntity.getReceivedContentMic();
        MicUtils.ReceivedContentMic computedContentMic
                = MicUtils.createReceivedContentMic(
                        (ClassicHttpRequest) requestHandler.getRequest(), new Certificate[] { clientCert },
                        null);

        assertEquals(computedContentMic.getEncodedMessageDigest(), receivedContentMic.getEncodedMessageDigest(),
                "Received content MIC does not match computed");
    }

    private DispositionNotificationMultipartReportEntity verifyAsyncResponse(String mock) throws Exception {
        Exchange exchange = receiveFromMock(mock);
        Message message = exchange.getIn();
        assertNotNull(message);
        assertTrue(message.getBody() instanceof DispositionNotificationMultipartReportEntity);

        return (DispositionNotificationMultipartReportEntity) message.getBody();
    }

    private MultipartSignedEntity verifySignedAsyncResponse(String mock) throws Exception {
        Exchange exchange = receiveFromMock(mock);
        Message message = exchange.getIn();
        assertNotNull(message);
        assertTrue(message.getBody() instanceof MultipartSignedEntity);

        return (MultipartSignedEntity) message.getBody();
    }

    // Request asynchronous receipt by including a 'receiptDeliveryOption' header specifying the return url.
    private DispositionNotificationMultipartReportEntity executeRequestWithAsyncResponseHeader(
            String endpointUri, int port, String mock)
            throws Exception {
        executeRequestAsyncHeader(endpointUri, getAS2HeadersForAsyncReceipt("http://localhost:" + port + "/handle-receipts"));

        return verifyAsyncResponse(mock);
    }

    // Request asynchronous receipt by including a 'receiptDeliveryOption' path parameter in the endpoint uri
    // specifying the return url.
    private DispositionNotificationMultipartReportEntity executeRequestWithAsyncResponsePath(String endpointUri, String mock)
            throws Exception {
        executeRequestAsyncPath(endpointUri, getAS2Headers());

        return verifyAsyncResponse(mock);
    }

    // Request signed asynchronous receipt by including a 'receiptDeliveryOption' header specifying the return url,
    // and a 'signedReceiptMicAlgorithms' header specifying the signing algorithms.
    private MultipartSignedEntity executeRequestWithSignedAsyncResponseHeader(String endpointUri, int port, String mock)
            throws Exception {
        Map<String, Object> headers = getAS2HeadersForAsyncReceipt("http://localhost:" + port + "/handle-receipts");
        addSignedMessageHeaders(headers);
        // In order to receive signed MDN receipts the client must include both the 'signed-receipt-protocol' and
        // the 'signed-receipt-micalg' option parameters.
        executeRequestAsyncHeader(endpointUri, headers);

        return verifySignedAsyncResponse(mock);
    }

    // Request a signed asynchronous receipt by including a 'receiptDeliveryOption' path parameter in the endpoint uri
    // specifying the return url, and a 'signedReceiptMicAlgorithms' header specifying the signing algorithms.
    private MultipartSignedEntity executeRequestWithSignedAsyncResponsePath(String endpointUri, String mock) throws Exception {
        Map<String, Object> headers = getAS2Headers();
        addSignedMessageHeaders(headers);
        // In order to receive signed MDN receipts the client must include both the 'signed-receipt-protocol' and
        // the 'signed-receipt-micalg' option parameters.
        executeRequestAsyncPath(endpointUri, headers);

        return verifySignedAsyncResponse(mock);
    }

    private void addSignedMessageHeaders(Map<String, Object> headers) {
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.SIGNED);
        headers.put("CamelAs2.signedReceiptMicAlgorithms", SIGNED_RECEIPT_MIC_ALGORITHMS);
        headers.put("CamelAs2.signingCertificateChain", new Certificate[] { clientCert });
        headers.put("CamelAs2.signingPrivateKey", clientKeyPair.getPrivate());
        headers.put("CamelAs2.signingAlgorithm", AS2SignatureAlgorithm.SHA512WITHRSA);
    }

    // Headers required for a client to call the AS2 'send' api.
    private Map<String, Object> getAS2Headers() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("CamelAs2.requestUri", REQUEST_URI);
        headers.put("CamelAs2.subject", SUBJECT);
        headers.put("CamelAs2.from", FROM);
        headers.put("CamelAs2.as2From", AS2_NAME);
        headers.put("CamelAs2.as2To", AS2_NAME);
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.PLAIN);
        headers.put("CamelAs2.ediMessageContentType", AS2MediaType.APPLICATION_EDIFACT);
        headers.put("CamelAs2.ediMessageTransferEncoding", EDI_MESSAGE_CONTENT_TRANSFER_ENCODING);
        headers.put("CamelAs2.dispositionNotificationTo", "mrAS2@example.com");

        return headers;
    }

    // Headers requesting that the AS2-MDN (receipt) be returned asynchronously
    private Map<String, Object> getAS2HeadersForAsyncReceipt(String deliveryAddress) {
        Map<String, Object> headers = getAS2Headers();
        headers.put("CamelAs2.receiptDeliveryOption", deliveryAddress);

        return headers;
    }

    private Exchange receiveFromMock(String mockUri) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(mockUri);
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges);
        assertFalse(exchanges.isEmpty());

        return exchanges.get(0);
    }

    private Triple<HttpEntity, HttpRequest, HttpResponse> executeRequestAsyncHeader(
            String endpointUri, Map<String, Object> headers)
            throws Exception {
        HttpEntity responseEntity = requestBodyAndHeaders(endpointUri, EDI_MESSAGE, headers);

        return new ImmutableTriple<>(responseEntity, requestHandler.getRequest(), requestHandler.getResponse());
    }

    private Triple<HttpEntity, HttpRequest, HttpResponse> executeRequestAsyncPath(
            String endpointUri, Map<String, Object> headers)
            throws Exception {
        HttpEntity responseEntity = requestBodyAndHeaders(endpointUri, EDI_MESSAGE, headers);

        return new ImmutableTriple<>(responseEntity, requestHandler.getRequest(), requestHandler.getResponse());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // with option for asynchronous receipt specified as header
                from("direct://SEND")
                        .to("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m");

                // with option for asynchronous receipt specified as path-param
                from("direct://SEND3")
                        .toF("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m"
                             + "&receiptDeliveryOption=%s", "http://localhost:" + RECEIPT_SERVER_PORT3 + "/handle-receipts");

                from("direct://SEND4")
                        .toF("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m"
                             + "&receiptDeliveryOption=%s", "http://localhost:" + RECEIPT_SERVER_PORT4 + "/handle-receipts");

                // asynchronous AS2-MDN (receipt) server instance
                fromF("as2://receipt/receive?requestUriPattern=/handle-receipts&asyncMdnPortNumber=%s",
                        RECEIPT_SERVER_PORT)
                        .to("mock:as2RcvRcptMsgs");

                fromF("as2://receipt/receive?requestUriPattern=/handle-receipts&asyncMdnPortNumber=%s",
                        RECEIPT_SERVER_PORT2)
                        .to("mock:as2RcvRcptMsgs2");

                fromF("as2://receipt/receive?requestUriPattern=/handle-receipts&asyncMdnPortNumber=%s",
                        RECEIPT_SERVER_PORT3)
                        .to("mock:as2RcvRcptMsgs3");

                fromF("as2://receipt/receive?requestUriPattern=/handle-receipts&asyncMdnPortNumber=%s",
                        RECEIPT_SERVER_PORT4)
                        .to("mock:as2RcvRcptMsgs4");
            }
        };
    }

    public static class RequestHandler implements HttpRequestHandler {

        private HttpRequest request;
        private HttpResponse response;

        @Override
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
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

    private static void receiveTestMessages() throws IOException {
        serverConnection = new AS2ServerConnection(
                AS2_VERSION, ORIGIN_SERVER_NAME,
                SERVER_FQDN, PARTNER_TARGET_PORT, AS2SignatureAlgorithm.SHA256WITHRSA,
                new Certificate[] { serverCert }, serverKP.getPrivate(), null,
                MDN_MESSAGE_TEMPLATE, new Certificate[] { clientCert }, null, null, null, null);
        requestHandler = new RequestHandler();
        serverConnection.listen("/", requestHandler);
    }

    private static void setupKeysAndCertificates() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // set up our certificates
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        KeyPair issueKP = kpg.generateKeyPair();
        X509Certificate issueCert = Utils.makeCertificate(issueKP, issueDN, issueKP, issueDN);

        // certificate we sign against
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        serverKP = kpg.generateKeyPair();
        serverCert = Utils.makeCertificate(serverKP, signingDN, issueKP, issueDN);

        clientKeyPair = kpg.generateKeyPair();
        clientCert = Utils.makeCertificate(clientKeyPair, signingDN, issueKP, issueDN);
    }
}
