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
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.EncryptingUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.Args;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.operator.OutputEncryptor;

/**
 * AS2 Client Manager
 *
 * <p>
 * Sends EDI Messages over HTTP
 *
 */
public class AS2ClientManager {

    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the AS2 Client
     * Manager.
     */
    public static final String CAMEL_AS2_CLIENT_PREFIX = "camel-as2.client.";

    /**
     * The HTTP Context Attribute indicating the AS2 message structure to be sent.
     */
    public static final String AS2_MESSAGE_STRUCTURE = CAMEL_AS2_CLIENT_PREFIX + "as2-message-structure";

    /**
     * The HTTP Context Attribute indicating the EDI message content type to be sent.
     */
    public static final String EDI_MESSAGE_CONTENT_TYPE = CAMEL_AS2_CLIENT_PREFIX + "edi-message-content-type";

    /**
     * The HTTP Context Attribute indicating the EDI message transfer encoding to be sent.
     */
    public static final String EDI_MESSAGE_TRANSFER_ENCODING = CAMEL_AS2_CLIENT_PREFIX + "edi-message-transfer-encoding";

    /**
     * The HTTP Context Attribute containing the HTTP request message
     * transporting the EDI message
     */
    public static final String HTTP_REQUEST = HttpCoreContext.HTTP_REQUEST;

    /**
     * The HTTP Context Attribute containing the HTTP response message
     * transporting the EDI message
     */
    public static final String HTTP_RESPONSE = HttpCoreContext.HTTP_RESPONSE;

    /**
     * The HTTP Context Attribute containing the AS2 Connection used to send
     * request message.
     */
    public static final String AS2_CONNECTION = CAMEL_AS2_CLIENT_PREFIX + "as2-connection";

    /**
     * The HTTP Context Attribute containing the request URI identifying the
     * process on the receiving system responsible for unpacking and handling of
     * message data and generating a reply for the sending system that contains
     * a Message Disposition Acknowledgement (MDN)
     */
    public static final String REQUEST_URI = CAMEL_AS2_CLIENT_PREFIX + "request-uri";

    /**
     * The HTTP Context Attribute containing the subject header sent in an AS2
     * message.
     */
    public static final String SUBJECT = CAMEL_AS2_CLIENT_PREFIX + "subject";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of
     * sending system
     */
    public static final String FROM = CAMEL_AS2_CLIENT_PREFIX + "from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * sending system
     */
    public static final String AS2_FROM = CAMEL_AS2_CLIENT_PREFIX + "as2-from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * receiving system
     */
    public static final String AS2_TO = CAMEL_AS2_CLIENT_PREFIX + "as2-to";

    /**
     * The HTTP Context Attribute containing the algorithm used to sign EDI
     * message
     */
    public static final String SIGNING_ALGORITHM = CAMEL_AS2_CLIENT_PREFIX + "signing-algorithm";

    /**
     * The HTTP Context Attribute containing the certificate chain used to sign
     * EDI message
     */
    public static final String SIGNING_CERTIFICATE_CHAIN = CAMEL_AS2_CLIENT_PREFIX + "signing-certificate-chain";

    /**
     * The HTTP Context Attribute containing the private key used to sign EDI
     * message
     */
    public static final String SIGNING_PRIVATE_KEY = CAMEL_AS2_CLIENT_PREFIX + "signing-private-key";

    /**
     * The HTTP Context Attribute containing the algorithm name used to encrypt EDI
     * message
     */
    public static final String ENCRYPTING_ALGORITHM = CAMEL_AS2_CLIENT_PREFIX + "encrypting-algorithm-name";

    /**
     * The HTTP Context Attribute containing the certificate used to encrypt
     * EDI message
     */
    public static final String ENCRYPTING_CERTIFICATE_CHAIN = CAMEL_AS2_CLIENT_PREFIX + "encrypting-certificate-chain";

    /**
     * The HTTP Context Attribute containing the private key used to encrypt EDI
     * message
     */
    public static final String ENCRYPTING_PRIVATE_KEY = CAMEL_AS2_CLIENT_PREFIX + "encrypting-private-key";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of
     * sending system requesting a message disposition notification.
     */
    public static final String DISPOSITION_NOTIFICATION_TO = CAMEL_AS2_CLIENT_PREFIX + "disposition-notification-to";

    /**
     * The HTTP Context Attribute containing the list of names of the requested MIC algorithms to be used
     * by the receiving system to construct a message disposition notification.
     */
    public static final String SIGNED_RECEIPT_MIC_ALGORITHMS = CAMEL_AS2_CLIENT_PREFIX + "signed-receipt-mic-algorithms";

    //

    private AS2ClientConnection as2ClientConnection;

    public AS2ClientManager(AS2ClientConnection as2ClientConnection) {
        this.as2ClientConnection = as2ClientConnection;
    }

    /**
     * Send <code>ediMessage</code> to trading partner.
     *
     * @param ediMessage
     *            - EDI message to transport
     * @param requestUri
     *            - resource location to deliver message
     * @param subject - message subject
     * @param from - RFC2822 address of sender
     * @param as2From - AS2 name of sender
     * @param as2To - AS2 name of recipient
     * @param as2MessageStructure - the structure of AS2 to send; see {@link AS2MessageStructure}
     * @param ediMessageContentType - the content typw of EDI message
     * @param ediMessageTransferEncoding - the transfer encoding used to transport EDI message
     * @param signingAlgorithm - the algorithm used to sign the message or <code>null</code> if sending EDI message unsigned
     * @param signingCertificateChain - the chain of certificates used to sign the message or <code>null</code> if sending EDI message unsigned
     * @param signingPrivateKey - the private key used to sign EDI message
     * @param dispositionNotificationTo - an RFC2822 address to request a receipt or <code>null</code> if no receipt requested
     * @param signedReceiptMicAlgorithms - the senders list of signing algorithms for signing receipt, in preferred order,  or <code>null</code> if requesting an unsigned receipt.
     * @param encryptingAlgorithm - the algorithm used to encrypt the message or <code>null</code> if sending EDI message unencrypted
     * @param encryptingCertificateChain - the chain of certificates used to encrypt the message or <code>null</code> if sending EDI message unencrypted
     * @param encryptingPrivateKey - the private key used to encrypt EDI message
     * @return {@link HttpCoreContext} containing request and response used to send EDI message
     * @throws HttpException when things go wrong.
     */
    public HttpCoreContext send(String ediMessage,
                                String requestUri,
                                String subject,
                                String from,
                                String as2From,
                                String as2To,
                                AS2MessageStructure as2MessageStructure,
                                ContentType ediMessageContentType,
                                String ediMessageTransferEncoding,
                                AS2SignatureAlgorithm signingAlgorithm,
                                Certificate[] signingCertificateChain,
                                PrivateKey signingPrivateKey,
                                String dispositionNotificationTo,
                                String[] signedReceiptMicAlgorithms,
                                AS2EncryptionAlgorithm encryptingAlgorithm,
                                Certificate[] encryptingCertificateChain,
                                PrivateKey encryptingPrivateKey)
            throws HttpException {

        Args.notNull(ediMessage, "EDI Message");
        Args.notNull(as2MessageStructure, "AS2 Message Structure");
        Args.notNull(requestUri, "Request URI");
        Args.notNull(ediMessageContentType, "EDI Message Content Type");

        // Add Context attributes
        HttpCoreContext httpContext = HttpCoreContext.create();
        httpContext.setAttribute(AS2ClientManager.REQUEST_URI, requestUri);
        httpContext.setAttribute(AS2ClientManager.SUBJECT, subject);
        httpContext.setAttribute(AS2ClientManager.FROM, from);
        httpContext.setAttribute(AS2ClientManager.AS2_FROM, as2From);
        httpContext.setAttribute(AS2ClientManager.AS2_TO, as2To);
        httpContext.setAttribute(AS2ClientManager.AS2_MESSAGE_STRUCTURE, as2MessageStructure);
        httpContext.setAttribute(AS2ClientManager.EDI_MESSAGE_CONTENT_TYPE, ediMessageContentType);
        httpContext.setAttribute(AS2ClientManager.EDI_MESSAGE_TRANSFER_ENCODING, ediMessageTransferEncoding);
        httpContext.setAttribute(AS2ClientManager.SIGNING_ALGORITHM, signingAlgorithm);
        httpContext.setAttribute(AS2ClientManager.SIGNING_CERTIFICATE_CHAIN, signingCertificateChain);
        httpContext.setAttribute(AS2ClientManager.SIGNING_PRIVATE_KEY, signingPrivateKey);
        httpContext.setAttribute(AS2ClientManager.DISPOSITION_NOTIFICATION_TO, dispositionNotificationTo);
        httpContext.setAttribute(AS2ClientManager.SIGNED_RECEIPT_MIC_ALGORITHMS, signedReceiptMicAlgorithms);
        httpContext.setAttribute(AS2ClientManager.ENCRYPTING_ALGORITHM, encryptingAlgorithm);
        httpContext.setAttribute(AS2ClientManager.ENCRYPTING_CERTIFICATE_CHAIN, encryptingCertificateChain);
        httpContext.setAttribute(AS2ClientManager.ENCRYPTING_PRIVATE_KEY, encryptingPrivateKey);

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", requestUri);
        httpContext.setAttribute(HTTP_REQUEST, request);

        // Create Message Body
        ApplicationEDIEntity applicationEDIEntity;
        try {
            applicationEDIEntity = EntityUtils.createEDIEntity(ediMessage, ediMessageContentType, ediMessageTransferEncoding, false);
        } catch (Exception e) {
            throw new HttpException("Failed to create EDI message entity", e);
        }
        switch (as2MessageStructure) {
        case PLAIN: {
            applicationEDIEntity.setMainBody(true);
            EntityUtils.setMessageEntity(request, applicationEDIEntity);
            break;
        }
        case SIGNED: {
            AS2SignedDataGenerator gen = createSigningGenerator(httpContext);
            // Create Multipart Signed Entity
            MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(applicationEDIEntity, gen,
                    AS2Charset.US_ASCII, AS2TransferEncoding.BASE64, true, null);
            multipartSignedEntity.setMainBody(true);
            EntityUtils.setMessageEntity(request, multipartSignedEntity);
            break;
        }
        case ENCRYPTED: {
            CMSEnvelopedDataGenerator envelopedDataGenerator = createEncryptingGenerator(httpContext);
            OutputEncryptor encryptor = createEncryptor(httpContext);
            ApplicationPkcs7MimeEntity pkcs7MimeEntity = new ApplicationPkcs7MimeEntity(applicationEDIEntity, envelopedDataGenerator, encryptor, AS2TransferEncoding.BASE64, true);
            EntityUtils.setMessageEntity(request, pkcs7MimeEntity);
            break;
        }
        case ENCRYPTED_SIGNED: {
            AS2SignedDataGenerator signingGenrator = createSigningGenerator(httpContext);
            MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(applicationEDIEntity,
                    signingGenrator, AS2Charset.US_ASCII, AS2TransferEncoding.BASE64, false, null);
             
            CMSEnvelopedDataGenerator envelopedDataGenerator = createEncryptingGenerator(httpContext);
            OutputEncryptor encryptor = createEncryptor(httpContext);
            ApplicationPkcs7MimeEntity pkcs7MimeEntity = new ApplicationPkcs7MimeEntity(multipartSignedEntity, envelopedDataGenerator, encryptor, AS2TransferEncoding.BASE64, true);
            EntityUtils.setMessageEntity(request, pkcs7MimeEntity);
            break;
        }
        default:
            throw new HttpException("Unknown AS2 Message Structure");
        }

        HttpResponse response;
        try {
            httpContext.setAttribute(AS2_CONNECTION, as2ClientConnection);
            response = as2ClientConnection.send(request, httpContext);
            EntityParser.parseAS2MessageEntity(response);
        } catch (IOException e) {
            throw new HttpException("Failed to send http request message", e);
        }
        httpContext.setAttribute(HTTP_RESPONSE, response);
        return httpContext;
    }
    
    public AS2SignedDataGenerator createSigningGenerator(HttpCoreContext httpContext) throws HttpException {
        
        AS2SignatureAlgorithm signatureAlgorithm = httpContext.getAttribute(SIGNING_ALGORITHM, AS2SignatureAlgorithm.class);
        if (signatureAlgorithm == null) {
            throw new HttpException("Signing algorithm missing");
        }

        Certificate[] certificateChain = httpContext.getAttribute(SIGNING_CERTIFICATE_CHAIN, Certificate[].class);
        if (certificateChain == null) {
            throw new HttpException("Signing certificate chain missing");
        }

        PrivateKey privateKey = httpContext.getAttribute(SIGNING_PRIVATE_KEY, PrivateKey.class);
        if (privateKey == null) {
            throw new HttpException("Signing private key missing");
        }

        return SigningUtils.createSigningGenerator(signatureAlgorithm, certificateChain, privateKey);

    }

    public CMSEnvelopedDataGenerator createEncryptingGenerator(HttpCoreContext httpContext) throws HttpException {

        Certificate[] certificateChain = httpContext.getAttribute(ENCRYPTING_CERTIFICATE_CHAIN, Certificate[].class);
        if (certificateChain == null) {
            throw new HttpException("Encrypting certificate chain missing");
        }

        return EncryptingUtils.createEnvelopDataGenerator(certificateChain);

    }
    
    public OutputEncryptor createEncryptor(HttpCoreContext httpContext) throws HttpException {
        
        AS2EncryptionAlgorithm encryptionAlgorithm = httpContext.getAttribute(ENCRYPTING_ALGORITHM, AS2EncryptionAlgorithm.class);
        if (encryptionAlgorithm == null) {
            throw new HttpException("Encrypting algorithm missing");
        }

        return EncryptingUtils.createEncryptor(encryptionAlgorithm);
    }

}
