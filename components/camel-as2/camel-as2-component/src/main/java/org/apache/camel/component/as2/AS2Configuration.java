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

import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.http.entity.ContentType;

/**
 * Component configuration for AS2 component.
 */
@UriParams
public class AS2Configuration {

    @UriPath
    @Metadata(required = true, enums = "client,server")
    private AS2ApiName apiName;

    @UriPath
    @Metadata(required = true)
    private String methodName;

    @UriParam(defaultValue = "1.1", enums = "1.0,1.1")
    private String as2Version = "1.1";

    @UriParam(defaultValue = "Camel AS2 Client Endpoint")
    private String userAgent = "Camel AS2 Client Endpoint";

    @UriParam(defaultValue = "Camel AS2 Server Endpoint")
    private String server = "Camel AS2 Server Endpoint";

    @UriParam(defaultValue = "camel.apache.org")
    private String serverFqdn = "camel.apache.org";

    @UriParam
    private String targetHostname;

    @UriParam
    private Integer targetPortNumber = 80;

    @UriParam(defaultValue = "camel.apache.org")
    private String clientFqdn = "camel.apache.org";

    @UriParam
    private Integer serverPortNumber;

    @UriParam(defaultValue = "/")
    private String requestUri = "/";

    @UriParam
    private ContentType ediMessageType;

    @UriParam
    private String ediMessageTransferEncoding;

    @UriParam
    private AS2MessageStructure as2MessageStructure;

    @UriParam
    private String subject;

    @UriParam
    private String from;

    @UriParam
    private String as2From;

    @UriParam
    private String as2To;

    @UriParam
    private AS2SignatureAlgorithm signingAlgorithm;

    @UriParam
    private Certificate[] signingCertificateChain;

    @UriParam
    private PrivateKey signingPrivateKey;

    @UriParam
    private AS2CompressionAlgorithm compressionAlgorithm;

    @UriParam
    private String dispositionNotificationTo;

    @UriParam
    private String[] signedReceiptMicAlgorithms;

    @UriParam
    private AS2EncryptionAlgorithm encryptingAlgorithm;

    @UriParam
    private Certificate[] encryptingCertificateChain;

    @UriParam
    private PrivateKey decryptingPrivateKey;
    
    @UriParam
    private String mdnMessageTemplate;

    public AS2ApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(AS2ApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getAs2Version() {
        return as2Version;
    }

    /**
     * The version of the AS2 protocol.
     */
    public void setAs2Version(String as2Version) {
        if (!as2Version.equals("1.0") && !as2Version.equals("1.1")) {
            throw new IllegalArgumentException(String.format(
                    "Value '%s' of configuration parameter 'as2Version' must be either '1.0' or '1.1'", as2Version));
        }
        this.as2Version = as2Version;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * The value included in the User-Agent message header identifying
     * the AS2 user agent.
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getServer() {
        return server;
    }

    /**
     * The value included in the Server message header identifying the
     * AS2 Server.
     */
    public void setServer(String server) {
        this.server = server;
    }

    public String getServerFqdn() {
        return serverFqdn;
    }

    /**
     * The Server Fully Qualified Domain Name (FQDN).
     * Used in message ids sent by endpoint.
     */
    public void setServerFqdn(String serverFqdn) {
        if (serverFqdn == null) {
            throw new RuntimeCamelException("Parameter 'serverFqdn' can not be null");
        }
        this.serverFqdn = serverFqdn;
    }

    public String getTargetHostname() {
        return targetHostname;
    }

    /**
     * The host name (IP or DNS name) of target host.
     */
    public void setTargetHostname(String targetHostname) {
        if (targetHostname == null) {
            throw new RuntimeCamelException("Parameter 'targetHostname' can not be null");
        }
        this.targetHostname = targetHostname;
    }

    public int getTargetPortNumber() {
        return targetPortNumber;
    }

    /**
     * The port number of target host. -1 indicates the scheme default port.
     */
    public void setTargetPortNumber(String targetPortNumber) {
        try {
            this.targetPortNumber = Integer.valueOf(targetPortNumber);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid target port number: %s", targetPortNumber));
        }
    }

    /**
     * The port number of target host. -1 indicates the scheme default port.
     */
    public void setTargetPortNumber(Integer targetPortNumber) {
        this.targetPortNumber = targetPortNumber;
    }

    public String getClientFqdn() {
        return clientFqdn;
    }

    /**
     * The Client Fully Qualified Domain Name (FQDN).
     * Used in message ids sent by endpoint.
     */
    public void setClientFqdn(String clientFqdn) {
        if (clientFqdn == null) {
            throw new RuntimeCamelException("Parameter 'clientFqdn' can not be null");
        }
        this.clientFqdn = clientFqdn;
    }

    public Integer getServerPortNumber() {
        return serverPortNumber;
    }

    /**
     * The port number of server.
     */
    public void setServerPortNumber(Integer serverPortNumber) {
        this.serverPortNumber = serverPortNumber;
    }

    public String getRequestUri() {
        return requestUri;
    }

    /**
     * The request URI of EDI message.
     */
    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public ContentType getEdiMessageType() {
        return ediMessageType;
    }

    /**
     * The content type of EDI message.
     * One of application/edifact, application/edi-x12, application/edi-consent
     */
    public void setEdiMessageType(ContentType ediMessageType) {
        this.ediMessageType = ediMessageType;
    }

    public String getEdiMessageTransferEncoding() {
        return ediMessageTransferEncoding;
    }

    /**
     * The transfer encoding of EDI message.
     */
    public void setEdiMessageTransferEncoding(String ediMessageTransferEncoding) {
        this.ediMessageTransferEncoding = ediMessageTransferEncoding;
    }

    public AS2MessageStructure getAs2MessageStructure() {
        return as2MessageStructure;
    }

    /**
     * The structure of AS2 Message. One of:
     * PLAIN - No encryption, no signature,
     * SIGNED - No encryption, signature,
     * ENCRYPTED - Encryption, no signature,
     * ENCRYPTED_SIGNED - Encryption, signature
     */
    public void setAs2MessageStructure(AS2MessageStructure as2MessageStructure) {
        this.as2MessageStructure = as2MessageStructure;
    }

    public String getSubject() {
        return subject;
    }

    /**
     * The value of Subject header of AS2 message.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    /**
     * The value of the From header of AS2 message.
     */
    public void setFrom(String from) {
        this.from = from;
    }

    public String getAs2From() {
        return as2From;
    }

    /**
     * The value of the AS2From header of AS2 message.
     */
    public void setAs2From(String as2From) {
        this.as2From = as2From;
    }

    public String getAs2To() {
        return as2To;
    }

    /**
     * The value of the AS2To header of AS2 message.
     */
    public void setAs2To(String as2To) {
        this.as2To = as2To;
    }

    public AS2SignatureAlgorithm getSigningAlgorithm() {
        return signingAlgorithm;
    }

    /**
     * The algorithm used to sign EDI message.
     */
    public void setSigningAlgorithm(AS2SignatureAlgorithm signingAlgorithm) {
        this.signingAlgorithm = signingAlgorithm;
    }

    public Certificate[] getSigningCertificateChain() {
        return signingCertificateChain;
    }

    /**
     * The chain of certificates used to sign EDI message.
     */
    public void setSigningCertificateChain(Certificate[] signingCertificateChain) {
        this.signingCertificateChain = signingCertificateChain;
    }

    public PrivateKey getSigningPrivateKey() {
        return signingPrivateKey;
    }

    /**
     * The key used to sign the EDI message.
     */
    public void setSigningPrivateKey(PrivateKey signingPrivateKey) {
        this.signingPrivateKey = signingPrivateKey;
    }

    public AS2CompressionAlgorithm getCompressionAlgorithm() {
        return compressionAlgorithm;
    }

    /**
     * The algorithm used to compress EDI message.
     */
    public void setCompressionAlgorithm(AS2CompressionAlgorithm compressionAlgorithm) {
        this.compressionAlgorithm = compressionAlgorithm;
    }

    public String getDispositionNotificationTo() {
        return dispositionNotificationTo;
    }

    /**
     * The value of the Disposition-Notification-To header.
     * 
     * Assigning a value to this parameter requests a message disposition
     * notification (MDN) for the AS2 message.
     */
    public void setDispositionNotificationTo(String dispositionNotificationTo) {
        this.dispositionNotificationTo = dispositionNotificationTo;
    }

    public String[] getSignedReceiptMicAlgorithms() {
        return signedReceiptMicAlgorithms;
    }

    /**
     * The list of algorithms, in order of preference, requested to generate a
     * message integrity check (MIC) returned in message dispostion notification
     * (MDN)
     */
    public void setSignedReceiptMicAlgorithms(String[] signedReceiptMicAlgorithms) {
        this.signedReceiptMicAlgorithms = signedReceiptMicAlgorithms;
    }

    public AS2EncryptionAlgorithm getEncryptingAlgorithm() {
        return encryptingAlgorithm;
    }

    /**
     * The algorithm used to encrypt EDI message.
     */
    public void setEncryptingAlgorithm(AS2EncryptionAlgorithm encryptingAlgorithm) {
        this.encryptingAlgorithm = encryptingAlgorithm;
    }

    public Certificate[] getEncryptingCertificateChain() {
        return encryptingCertificateChain;
    }

    /**
     * The chain of certificates used to encrypt EDI message.
     */
    public void setEncryptingCertificateChain(Certificate[] signingCertificateChain) {
        this.encryptingCertificateChain = signingCertificateChain;
    }

    public PrivateKey getDecryptingPrivateKey() {
        return decryptingPrivateKey;
    }

    /**
     * The key used to encrypt the EDI message.
     */
    public void setDecryptingPrivateKey(PrivateKey signingPrivateKey) {
        this.decryptingPrivateKey = signingPrivateKey;
    }

    public String getMdnMessageTemplate() {
        return mdnMessageTemplate;
    }

    /**
     * The template used to format MDN message
     */
    public void setMdnMessageTemplate(String mdnMessageTemplate) {
        this.mdnMessageTemplate = mdnMessageTemplate;
    }
    
    
}
