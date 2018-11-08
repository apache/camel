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
package org.apache.camel.component.as2.api.protocol;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.AS2AsynchronousMDNManager;
import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.InvalidAS2NameException;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.AS2Utils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseMDN implements HttpResponseInterceptor {

    public static final String BOUNDARY_PARAM_NAME = "boundary";

    private static final Logger LOG = LoggerFactory.getLogger(ResponseMDN.class);

    private final String as2Version;
    private final String serverFQDN;
    private AS2SignatureAlgorithm signingAlgorithm;
    private Certificate[] signingCertificateChain;
    private PrivateKey signingPrivateKey;

    public ResponseMDN(String as2Version, String serverFQDN, AS2SignatureAlgorithm signingAlgorithm, Certificate[] signingCertificateChain, PrivateKey signingPrivateKey) {
        this.as2Version = as2Version;
        this.serverFQDN = serverFQDN;
        this.signingAlgorithm = signingAlgorithm;
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            // RFC4130 - 7.6 - Status codes in the 200 range SHOULD also be used when an entity is returned
            // (a signed receipt in a multipart/signed content type or an unsigned
            // receipt in a multipart/report)
            LOG.debug("MDN not return due to response status code: {}", statusCode);
            return;
        }

        HttpCoreContext coreContext = HttpCoreContext.adapt(context);

        HttpEntityEnclosingRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpEntityEnclosingRequest.class);
        if (request == null) {
            // Should never happen; but you never know
            LOG.debug("MDN not returned due to null request");
            throw new HttpException("request missing from HTTP context");
        }
        LOG.debug("Processing MDN for request: {}", request);

        if (HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_TO) == null) {
            // no receipt requested by sender
            LOG.debug("MDN not returned: no receipt requested");
            return;
        }

        // Return a Message Disposition Notification Receipt in response body
        String boundary = EntityUtils.createBoundaryValue();
        DispositionNotificationMultipartReportEntity multipartReportEntity = new DispositionNotificationMultipartReportEntity(
                request, response, DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                AS2DispositionType.PROCESSED, null, null, null, null, null, AS2Charset.US_ASCII, boundary, true);

        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser
                .parseDispositionNotificationOptions(
                        HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS), null);

        String receiptAddress = HttpMessageUtils.getHeaderValue(request, AS2Header.RECEIPT_DELIVERY_OPTION);
        if (receiptAddress != null) {
            // Asynchronous Delivery

            coreContext.setAttribute(AS2AsynchronousMDNManager.RECIPIENT_ADDRESS, receiptAddress);
            coreContext.setAttribute(AS2AsynchronousMDNManager.ASYNCHRONOUS_MDN, multipartReportEntity);
            
        } else {
            // Synchronous Delivery

            /* MIME header */
            response.addHeader(AS2Header.MIME_VERSION, AS2Constants.MIME_VERSION);

            /* AS2-Version header */
            response.addHeader(AS2Header.AS2_VERSION, as2Version);

            /* Subject header */
            // RFC4130 - 7.3 -  Subject header SHOULD be supplied
            String subjectPrefix = coreContext.getAttribute(AS2ServerManager.SUBJECT, String.class);
            String subject = HttpMessageUtils.getHeaderValue(request, AS2Header.SUBJECT);
            if (subjectPrefix != null && subject != null) {
                subject = subjectPrefix + subject;
            } else if (subject != null) {
                subject = "MDN Response To:" + subject;
            } else {
                subject = "Your Requested MDN Response";
            }
            response.addHeader(AS2Header.SUBJECT, subject);

            /* From header */
            String from = coreContext.getAttribute(AS2ServerManager.FROM, String.class);
            response.addHeader(AS2Header.FROM, from);

            /* AS2-From header */
            String as2From = HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_TO);
            try {
                AS2Utils.validateAS2Name(as2From);
            } catch (InvalidAS2NameException e) {
                throw new HttpException("Invalid AS-From name", e);
            }
            response.addHeader(AS2Header.AS2_FROM, as2From);

            /* AS2-To header */
            String as2To = HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_FROM);
            try {
                AS2Utils.validateAS2Name(as2To);
            } catch (InvalidAS2NameException e) {
                throw new HttpException("Invalid AS-To name", e);
            }
            response.addHeader(AS2Header.AS2_TO, as2To);

            /* Message-Id header*/
            // RFC4130 - 7.3 -  A Message-ID header is added to support message reconciliation
            response.addHeader(AS2Header.MESSAGE_ID, AS2Utils.createMessageId(serverFQDN));

            AS2SignedDataGenerator gen = null;
            if (dispositionNotificationOptions.getSignedReceiptProtocol() != null && signingCertificateChain != null
                    && signingPrivateKey != null) {
                gen = SigningUtils.createSigningGenerator(signingAlgorithm, signingCertificateChain, signingPrivateKey);
            }

            if (gen != null) {
                // Create signed receipt
                try {
                    multipartReportEntity.setMainBody(false);
                    MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(multipartReportEntity, gen,
                            AS2Charset.US_ASCII, AS2TransferEncoding.BASE64, false, null);
                    response.setHeader(multipartSignedEntity.getContentType());
                    EntityUtils.setMessageEntity(response, multipartSignedEntity);
                } catch (Exception e) {
                    LOG.warn("failed to sign receipt");
                }
            } else {
                // Create unsigned receipt
                response.setHeader(multipartReportEntity.getContentType());
                EntityUtils.setMessageEntity(response, multipartReportEntity);
            }
        }

        LOG.debug(AS2Utils.printMessage(response));
    }

}
