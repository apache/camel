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

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2ReportType;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.InvalidAS2NameException;
import org.apache.camel.component.as2.api.Util;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.http.Header;
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
    private Certificate[] signingCertificateChain;
    private PrivateKey signingPrivateKey;

    public ResponseMDN(String as2Version, String serverFQDN, Certificate[] signingCertificateChain, PrivateKey signingPrivateKey) {
        this.as2Version = as2Version;
        this.serverFQDN = serverFQDN;
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            LOG.debug("MDN not added due to response status code: " + statusCode);
            return;
        }
        LOG.debug("Adding MDN to response: " + response);

        HttpCoreContext coreContext = HttpCoreContext.adapt(context);

        HttpEntityEnclosingRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpEntityEnclosingRequest.class);
        if (request == null) {
            LOG.debug("MDN not added due to null request");
            return;
        }
        LOG.debug("Processing MDN for request: " + request);

        /* MIME header */
        response.addHeader(AS2Header.MIME_VERSION, AS2Constants.MIME_VERSION);

        /* AS2-Version header */
        response.addHeader(AS2Header.AS2_VERSION, as2Version);

        /* Subject header */
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
            Util.validateAS2Name(as2From);
        } catch (InvalidAS2NameException e) {
            throw new HttpException("Invalid AS-From name", e);
        }
        response.addHeader(AS2Header.AS2_FROM, as2From);

        /* AS2-To header */
        String as2To = HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_FROM);
        try {
            Util.validateAS2Name(as2To);
        } catch (InvalidAS2NameException e) {
            throw new HttpException("Invalid AS-To name", e);
        }
        response.addHeader(AS2Header.AS2_TO, as2To);

        /* Message-Id header*/
        // SHOULD be set to aid in message reconciliation
        response.addHeader(AS2Header.MESSAGE_ID, Util.createMessageId(serverFQDN));

        if (HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_TO) != null) {
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
                // TODO Implement
            } else {
                // Synchronous Delivery

                AS2SignedDataGenerator gen = null;
                if (dispositionNotificationOptions.getSignedReceiptProtocol() != null && signingCertificateChain != null && signingPrivateKey != null) {
                    gen = SigningUtils.createSigningGenerator(signingCertificateChain, signingPrivateKey);
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
                    Header reportTypeHeader = AS2HeaderUtils.createHeader(AS2Header.REPORT_TYPE, new String[][] {{AS2ReportType.DISPOSITION_NOTIFICATION}, {BOUNDARY_PARAM_NAME, boundary}});
                    response.addHeader(reportTypeHeader);
                    response.setHeader(AS2Header.CONTENT_TYPE, AS2MimeType.MULTIPART_REPORT);
                    EntityUtils.setMessageEntity(response, multipartReportEntity);
                }
            }

        }
        LOG.debug(Util.printMessage(response));
    }

}
