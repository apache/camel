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
package org.apache.camel.component.as2;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.api.AS2MessageStructure;
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
    @Metadata(required = "true")
    private AS2ApiName apiName;

    @UriPath
    @Metadata(required = "true")
    private String methodName;

    @UriPath
    private String as2Version = "1.1";

    @UriParam
    private String userAgent = "Camel AS2 Client Endpoint";

    @UriParam
    private String server = "Camel AS2 Server Endpoint";

    @UriParam
    private String serverFqdn = "camel.apache.org";

    @UriParam
    private String targetHostname;

    @UriParam
    private Integer targetPortNumber;

    @UriParam
    private String clientFqdn = "camel.apache.org";

    @UriParam
    private Integer serverPortNumber;

    @UriParam
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
    private String signingAlgorithmName;

    @UriParam
    private Certificate[] signingCertificateChain;

    @UriParam
    private PrivateKey signingPrivateKey;

    @UriParam
    private String dispositionNotificationTo;

    @UriParam
    private String[] signedReceiptMicAlgorithms;

    /**
     * What kind of operation to perform
     *
     * @return the API Name
     */
    public AS2ApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     *
     * @param apiName
     *            - the API Name to set
     */
    public void setApiName(AS2ApiName apiName) {
        this.apiName = apiName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @return The methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @param methodName
     *            - the methodName to set
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * The version of the AS2 protocol.
     *
     * @return The version of the AS2 protocol.
     */
    public String getAs2Version() {
        return as2Version;
    }

    /**
     * The version of the AS2 protocol.
     *
     * @param as2Version
     *            - the version of the AS2 protocol.
     */
    public void setAs2Version(String as2Version) {
        if (!as2Version.equals("1.0") && !as2Version.equals("1.1")) {
            throw new IllegalArgumentException(String.format(
                    "Value '%s' of configuration parameter 'as2Version' must be either '1.0' or '1.1'", as2Version));
        }
        this.as2Version = as2Version;
    }

    /**
     * The value included in the <code>User-Agent</code> message header identifying
     * the AS2 user agent.
     *
     * @return AS2 user agent identification string.
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * The value included in the <code>User-Agent</code> message header identifying
     * the AS2 user agent.
     *
     * @param userAgent
     *            - AS2 user agent identification string.
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * The value included in the <code>Server</code> message header identifying the
     * AS2 Server.
     *
     * @return AS2 server identification string.
     */
    public String getServer() {
        return server;
    }

    /**
     * The value included in the <code>Server</code> message header identifying the
     * AS2 Server.
     *
     * @param server
     *            - AS2 server identification string.
     */
    public void setServer(String server) {
        this.server = server;
    }

    /**
     * The Server Fully Qualified Domain Name (FQDN).
     *
     * <p>
     * Used in message ids sent by endpoint.
     *
     * @return The FQDN of client.
     */
    public String getServerFqdn() {
        return serverFqdn;
    }

    /**
     * The Server Fully Qualified Domain Name (FQDN).
     *
     * <p>
     * Used in message ids sent by endpoint.
     *
     * @param clientFqdn
     *            - the FQDN of server.
     */
    public void setServerFqdn(String serverFqdn) {
        if (clientFqdn == null) {
            throw new RuntimeCamelException("Parameter 'serverFqdn' can not be null");
        }
        this.serverFqdn = serverFqdn;
    }

    /**
     * The host name (IP or DNS) of target host.
     *
     * @return The target host name (IP or DNS name).
     */
    public String getTargetHostname() {
        return targetHostname;
    }

    /**
     * The host name (IP or DNS name) of target host.
     *
     * @param targetHostname
     *            - the target host name (IP or DNS name).
     */
    public void setTargetHostname(String targetHostname) {
        this.targetHostname = targetHostname;
    }

    /**
     * The port number of target host.
     *
     * @return The target port number. -1 indicates the scheme default port.
     */
    public int getTargetPortNumber() {
        return targetPortNumber;
    }

    /**
     * The port number of target host.
     *
     * @param targetPortNumber
     *            - the target port number. -1 indicates the scheme default port.
     */
    public void setTargetPortNumber(String targetPortNumber) {
        try {
            this.targetPortNumber = Integer.valueOf(targetPortNumber);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid target port number: %s", targetPortNumber));
        }
    }

    /**
     * The port number of target host.
     * 
     * @param targetPortNumber
     *            - the target port number. -1 indicates the scheme default port.
     */
    public void setTargetPortNumber(Integer targetPortNumber) {
        this.targetPortNumber = targetPortNumber;
    }

    /**
     * The Client Fully Qualified Domain Name (FQDN).
     *
     * <p>
     * Used in message ids sent by endpoint.
     *
     * @return The FQDN of client.
     */
    public String getClientFqdn() {
        return clientFqdn;
    }

    /**
     * The Client Fully Qualified Domain Name (FQDN).
     *
     * <p>
     * Used in message ids sent by endpoint.
     *
     * @param clientFqdn
     *            - the FQDN of client.
     */
    public void setClientFqdn(String clientFqdn) {
        if (clientFqdn == null) {
            throw new RuntimeCamelException("Parameter 'clientFqdn' can not be null");
        }
        this.clientFqdn = clientFqdn;
    }

    /**
     * The port number of server.
     *
     * @return The server port number.
     */
    public Integer getServerPortNumber() {
        return serverPortNumber;
    }

    /**
     * The port number of server.
     *
     * @param serverPortNumber
     *            - the server port number.
     */
    public void setServerPortNumber(String serverPortNumber) {
        try {
            this.serverPortNumber = Integer.valueOf(serverPortNumber);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid target port number: %s", targetPortNumber));
        }
    }

    /**
     * The port number of server.
     *
     * @param serverPortNumber
     *            - the server port number.
     */
    public void setServerPortNumber(Integer serverPortNumber) {
        this.serverPortNumber = serverPortNumber;
    }

    /**
     * The request URI of EDI message.
     * 
     * @return The URI of request
     */
    public String getRequestUri() {
        return requestUri;
    }

    /**
     * The request URI of EDI message.
     * 
     * @param requestUri
     *            - the URI of request
     */
    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    /**
     * The content type of EDI message. One of
     * <ul>
     * <li>application/edifact</li>
     * <li>application/edi-x12</li>
     * <li>application/edi-consent</li>
     * </ul>
     * 
     * @return The content type of EDI message.
     */
    public ContentType getEdiMessageType() {
        return ediMessageType;
    }

    /**
     * The content type of EDI message. One of
     * <ul>
     * <li>application/edifact</li>
     * <li>application/edi-x12</li>
     * <li>application/edi-consent</li>
     * </ul>
     * 
     * @param ediMessageType
     *            - the content type of EDI message.
     */
    public void setEdiMessageType(ContentType ediMessageType) {
        this.ediMessageType = ediMessageType;
    }

    /**
     * The transfer encoding of EDI message.
     * 
     * @return The transfer encoding of EDI message.
     */
    public String getEdiMessageTransferEncoding() {
        return ediMessageTransferEncoding;
    }

    /**
     * The transfer encoding of EDI message.
     * 
     * @param ediMessageTransferEncoding
     *            - the transfer encoding of EDI message.
     */
    public void setEdiMessageTransferEncoding(String ediMessageTransferEncoding) {
        this.ediMessageTransferEncoding = ediMessageTransferEncoding;
    }

    /**
     * The structure of AS2 Message. One of
     * <ul>
     * <li>PLAIN - No encryption, no signature</li>
     * <li>SIGNED - No encryption, signature</li>
     * <li>ENCRYPTED - Encryption, no signature</li>
     * <li>ENCRYPTED_SIGNED - Encryption, signature</li>
     * </ul>
     * 
     * @return The structure of AS2 Message.
     */
    public AS2MessageStructure getAs2MessageStructure() {
        return as2MessageStructure;
    }

    /**
     * The structure of AS2 Message. One of
     * <ul>
     * <li>PLAIN - No encryption, no signature</li>
     * <li>SIGNED - No encryption, signature</li>
     * <li>ENCRYPTED - Encryption, no signature</li>
     * <li>ENCRYPTED_SIGNED - Encryption, signature</li>
     * </ul>
     * 
     * @param as2MessageStructure
     *            - the structure of AS2 Message.
     */
    public void setAs2MessageStructure(AS2MessageStructure as2MessageStructure) {
        this.as2MessageStructure = as2MessageStructure;
    }

    /**
     * The value of Subject header of AS2 message.
     * 
     * @return The value of Subject header of AS2 message.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * The value of Subject header of AS2 message.
     * 
     * @param subject
     *            - the value of Subject header of AS2 message.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * The value of the From header of AS2 message.
     * 
     * @return The value of the From header of AS2 message.
     */
    public String getFrom() {
        return from;
    }

    /**
     * The value of the From header of AS2 message.
     * 
     * @param from
     *            - the value of the From header of AS2 message.
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * The value of the AS2From header of AS2 message.
     * 
     * @return The value of the AS2From header of AS2 message.
     */
    public String getAs2From() {
        return as2From;
    }

    /**
     * The value of the AS2From header of AS2 message.
     * 
     * @param as2From
     *            - the value of the AS2From header of AS2 message.
     */
    public void setAs2From(String as2From) {
        this.as2From = as2From;
    }

    /**
     * The value of the AS2To header of AS2 message.
     * 
     * @return The value of the AS2To header of AS2 message.
     */
    public String getAs2To() {
        return as2To;
    }

    /**
     * The value of the AS2To header of AS2 message.
     * 
     * @param as2From
     *            - the value of the AS2To header of AS2 message.
     */
    public void setAs2To(String as2To) {
        this.as2To = as2To;
    }

    /**
     * The name of algorithm used to sign EDI message.
     * 
     * @return The name of algorithm used to sign EDI message.
     */
    public String getSigningAlgorithmName() {
        return signingAlgorithmName;
    }

    /**
     * The name of algorithm used to sign EDI message.
     * 
     * @param signingAlgorithmName
     *            - the name of algorithm used to sign EDI message.
     */
    public void setSigningAlgorithmName(String signingAlgorithmName) {
        this.signingAlgorithmName = signingAlgorithmName;
    }

    /**
     * The chain of certificates used to sign EDI message.
     * 
     * @return The chain of certificates used to sign EDI message.
     */
    public Certificate[] getSigningCertificateChain() {
        return signingCertificateChain;
    }

    /**
     * The chain of certificates used to sign EDI message.
     * 
     * @param signingCertificateChain
     *            - the chain of certificates used to sign EDI message.
     */
    public void setSigningCertificateChain(Certificate[] signingCertificateChain) {
        this.signingCertificateChain = signingCertificateChain;
    }

    /**
     * The key used to sign the EDI message.
     * 
     * @return The key used to sign the EDI message.
     */
    public PrivateKey getSigningPrivateKey() {
        return signingPrivateKey;
    }

    /**
     * The key used to sign the EDI message.
     * 
     * @param signingPrivateKey
     *            - the key used to sign the EDI message.
     */
    public void setSigningPrivateKey(PrivateKey signingPrivateKey) {
        this.signingPrivateKey = signingPrivateKey;
    }

    /**
     * The value of the Dispostion-Notification-To header.
     * 
     * Assigning a value to this parameter requests a message disposition
     * notification (MDN) for the AS2 message.
     * 
     * @return The value of the Dispostion-Notification-To header.
     */
    public String getDispositionNotificationTo() {
        return dispositionNotificationTo;
    }

    /**
     * The value of the Dispostion-Notification-To header.
     * 
     * Assigning a value to this parameter requests a message disposition
     * notification (MDN) for the AS2 message.
     * 
     * @param dispositionNotificationTo
     *            - the value of the Dispostion-Notification-To header.
     */
    public void setDispositionNotificationTo(String dispositionNotificationTo) {
        this.dispositionNotificationTo = dispositionNotificationTo;
    }

    /**
     * The list of algorithms, in order of preference, requested to generate a
     * message integrity check (MIC) returned in message dispostion notification
     * (MDN)
     * 
     * @return The list of algorithms.
     */
    public String[] getSignedReceiptMicAlgorithms() {
        return signedReceiptMicAlgorithms;
    }

    /**
     * The list of algorithms, in order of preference, requested to generate a
     * message integrity check (MIC) returned in message dispostion notification
     * (MDN)
     * 
     * @param signedReceiptMicAlgorithms
     *            - the list of algorithms.
     */
    public void setSignedReceiptMicAlgorithms(String[] signedReceiptMicAlgorithms) {
        this.signedReceiptMicAlgorithms = signedReceiptMicAlgorithms;
    }

}
