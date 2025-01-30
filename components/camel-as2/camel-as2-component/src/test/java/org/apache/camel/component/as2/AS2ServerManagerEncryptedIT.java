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

import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.as2.api.AS2ServerManager} APIs that uses encrypted AS2 message
 * types.
 */
public class AS2ServerManagerEncryptedIT extends AS2ServerManagerITBase {

    @Test
    public void receiveEnvelopedMessageTest() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));

        AS2ClientConnection clientConnection
                = new AS2ClientConnection(
                        AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT, HTTP_SOCKET_TIMEOUT,
                        HTTP_CONNECTION_TIMEOUT, HTTP_CONNECTION_POOL_SIZE, HTTP_CONNECTION_POOL_TTL, clientSslContext,
                        null);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME, AS2MessageStructure.ENCRYPTED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null, null, null, null,
                null, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS, AS2EncryptionAlgorithm.AES128_CBC,
                certList.toArray(new Certificate[0]), null, null);

        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull(exchanges, "listen result");
        assertFalse(exchanges.isEmpty(), "listen result");

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

        String rcvdMessage = message.getBody(String.class);
        assertEquals(EDI_MESSAGE.replaceAll("[\n\r]", ""), rcvdMessage.replaceAll("[\n\r]", ""),
                "Unexpected content for enveloped mime part");
    }

    // Verify that the payload is compressed before being signed and encrypted.
    @Test
    public void receiveEnvelopedCompressedAndSignedMessageTest() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));

        AS2ClientConnection clientConnection
                = new AS2ClientConnection(
                        AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOST, TARGET_PORT, HTTP_SOCKET_TIMEOUT,
                        HTTP_CONNECTION_TIMEOUT, HTTP_CONNECTION_POOL_SIZE, HTTP_CONNECTION_POOL_TTL, clientSslContext,
                        null);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);

        clientManager.send(EDI_MESSAGE, REQUEST_URI, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.ENCRYPTED_COMPRESSED_SIGNED,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII), null,
                AS2SignatureAlgorithm.SHA256WITHRSA, certList.toArray(new Certificate[0]), signingKP.getPrivate(),
                AS2CompressionAlgorithm.ZLIB, DISPOSITION_NOTIFICATION_TO, SIGNED_RECEIPT_MIC_ALGORITHMS,
                AS2EncryptionAlgorithm.AES128_CBC,
                certList.toArray(new Certificate[0]), null, null);

        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        verifyExchanges(exchanges);

        HttpCoreContext coreContext = exchanges.get(0).getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        assertNotNull(coreContext, "context");

        HttpRequest request = coreContext.getRequest();
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
        MimeEntity decryptedEntity = envelopedEntity.getEncryptedEntity(decryptingKP.getPrivate());
        assertTrue(decryptedEntity instanceof MultipartSignedEntity, "Enveloped mime part incorrect type ");
        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) decryptedEntity;
        assertTrue(multipartSignedEntity.getContentType().startsWith(AS2MediaType.MULTIPART_SIGNED),
                "Unexpected content type for enveloped mime part");
        assertFalse(multipartSignedEntity.isMainBody(), "Enveloped mime type set as main body of request");

        verifyCompressedSignedEntity(multipartSignedEntity);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.clientSslContext = setupClientContext(context);
        this.serverSslContext = setupClientContext(context);
        AS2Component as2Component = (AS2Component) context.getComponent("as2");
        AS2Configuration configuration = as2Component.getConfiguration();
        configuration.setSslContext(serverSslContext);
        configuration.setDecryptingPrivateKey(decryptingKP.getPrivate());
        return context;
    }
}
