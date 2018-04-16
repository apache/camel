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
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.OperatorCreationException;

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
     * Prefix for all AS2 HTTP Context Attributes used by the Http Client
     * Manager.
     */
    public static final String CAMEL_AS2_PREFIX = "camel-as2.";

    /**
     * The HTTP Context Attribute indicating the AS2 message structure to be sent.
     */
    public static final String AS2_MESSAGE_STRUCTURE = CAMEL_AS2_PREFIX + "as1-message-structure";
    
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
    public static final String AS2_CONNECTION = CAMEL_AS2_PREFIX + "as2-connection";

    /**
     * The HTTP Context Attribute containing the request URI identifying the
     * process on the receiving system responsible for unpacking and handling of
     * message data and generating a reply for the sending system that contains
     * a Message Disposition Acknowledgement (MDN)
     */
    public static final String REQUEST_URI = CAMEL_AS2_PREFIX + "request-uri";

    /**
     * The HTTP Context Attribute containing the subject header sent in an AS2
     * message.
     */
    public static final String SUBJECT = CAMEL_AS2_PREFIX + "subject";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of
     * sending system
     */
    public static final String FROM = CAMEL_AS2_PREFIX + "from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * sending system
     */
    public static final String AS2_FROM = CAMEL_AS2_PREFIX + "as2-from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * receiving system
     */
    public static final String AS2_TO = CAMEL_AS2_PREFIX + "as2-to";

    /**
     * The HTTP Context Attribute containing the algorithm name used to sign EDI
     * message
     */
    public static final String SIGNING_ALGORITHM_NAME = CAMEL_AS2_PREFIX + "signing-algorithm-name";

    /**
     * The HTTP Context Attribute containing the certificate chain used to sign
     * EDI message
     */
    public static final String SIGNING_CERTIFICATE_CHAIN = CAMEL_AS2_PREFIX + "signing-certificate-chain";

    /**
     * The HTTP Context Attribute containing the private key used to sign EDI
     * message
     */
    public static final String SIGNING_PRIVATE_KEY = CAMEL_AS2_PREFIX + "signing-private-key";

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
     * @param httpContext
     *            - the subject sent in the interchange request.
     * @throws HttpException
     */
    public void send(String ediMessage, HttpCoreContext httpContext) throws HttpException {
        String requestUri = httpContext.getAttribute(REQUEST_URI, String.class);
        if (requestUri == null) {
            throw new HttpException("Request URI missing");
        }
        
        AS2MessageStructure messageStructure = httpContext.getAttribute(AS2_MESSAGE_STRUCTURE, AS2MessageStructure.class);
        if (messageStructure == null) {
            throw new HttpException("AS2 Message Structure missing");
        }

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", requestUri);
        httpContext.setAttribute(HTTP_REQUEST, request);

        // Create Message Body
        ApplicationEDIFACTEntity applicationEDIFACTEntity = new ApplicationEDIFACTEntity(ediMessage,
                AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, false);
        switch (messageStructure) {
        case PLAIN:
            applicationEDIFACTEntity.setMainBody(true);
            request.setEntity(applicationEDIFACTEntity);
            break;
        case SIGNED:
            AS2SignedDataGenerator gen = createSigningGenerator(httpContext);
            // Create Multipart Signed Entity
            MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(applicationEDIFACTEntity, gen,
                    AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, true, null);
            request.setEntity(multipartSignedEntity);
            break;
        case ENCRYPTED:
            break;
        case ENCRYPTED_SIGNED:
            break;
        }

        HttpResponse response;
        try {
            httpContext.setAttribute(AS2_CONNECTION, as2ClientConnection);
            response = as2ClientConnection.send(request, httpContext);
        } catch (IOException e) {
            throw new HttpException("Failed to send http request message", e);
        }
        httpContext.setAttribute(HTTP_RESPONSE, response);
    }

    /**
     * Send <code>ediMessage</code> unencrypted and signed to trading partner.
     * 
     * @param ediMessage
     *            - EDI message to transport
     * @param httpContext
     *            - context containing client sending attributes
     * @throws HttpException
     */
    public void sendSigned(String ediMessage, HttpCoreContext httpContext) throws HttpException {

        String requestUri = httpContext.getAttribute(REQUEST_URI, String.class);
        if (requestUri == null) {
            throw new HttpException("Request URI missing");
        }

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", requestUri);
        httpContext.setAttribute(HTTP_REQUEST, request);

        AS2SignedDataGenerator gen = createSigningGenerator(httpContext);

        // Create Application EDIFACT Mime Part
        ApplicationEDIFACTEntity applicationEDIFACTEntity = new ApplicationEDIFACTEntity(ediMessage,
                AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, false);

        // Create Multipart Signed Message Body
        MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(applicationEDIFACTEntity, gen,
                AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, false, null);
        request.setEntity(multipartSignedEntity);

        HttpResponse response;
        try {
            response = as2ClientConnection.send(request, httpContext);
        } catch (IOException e) {
            throw new HttpException("Failed to send http request message", e);
        }
        httpContext.setAttribute(HTTP_RESPONSE, response);

    }

    public AS2SignedDataGenerator createSigningGenerator(HttpCoreContext httpContext) throws HttpException {

        String algorithmName = httpContext.getAttribute(SIGNING_ALGORITHM_NAME, String.class);
        if (algorithmName == null) {
            throw new HttpException("Signing lgorithm name missing");
        }

        Certificate[] certificateChain = httpContext.getAttribute(SIGNING_CERTIFICATE_CHAIN, Certificate[].class);
        if (certificateChain == null) {
            throw new HttpException("Signing certificate chain missing");
        }

        PrivateKey privateKey = httpContext.getAttribute(SIGNING_PRIVATE_KEY, PrivateKey.class);
        if (privateKey == null) {
            throw new HttpException("Signing private key missing");
        }

        AS2SignedDataGenerator gen = new AS2SignedDataGenerator();

        // Get first certificate in chain for signing
        X509Certificate signingCert = (X509Certificate) certificateChain[0];

        // Create capabilities vector
        SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
        capabilities.addCapability(SMIMECapability.dES_EDE3_CBC);
        capabilities.addCapability(SMIMECapability.rC2_CBC, 128);
        capabilities.addCapability(SMIMECapability.dES_CBC);

        // Create signing attributes
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(new IssuerAndSerialNumber(
                new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
        attributes.add(new SMIMECapabilitiesAttribute(capabilities));

        try {
            gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC")
                    .setSignedAttributeGenerator(new AttributeTable(attributes))
                    .build(algorithmName, privateKey, signingCert));
        } catch (CertificateEncodingException | OperatorCreationException e) {
            throw new HttpException("Failed to add signer", e);
        }

        // Create and populate certificate store.
        try {
            JcaCertStore certs = new JcaCertStore(Arrays.asList(certificateChain));
            gen.addCertificates(certs);
        } catch (CertificateEncodingException | CMSException e) {
            throw new HttpException("Failed to add certificate chain to signature", e);
        }

        return gen;

    }

}
