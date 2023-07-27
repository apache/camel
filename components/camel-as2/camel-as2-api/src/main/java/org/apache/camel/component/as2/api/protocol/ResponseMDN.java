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
package org.apache.camel.component.as2.api.protocol;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.component.as2.api.AS2AsynchronousMDNManager;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.InvalidAS2NameException;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseMDN implements HttpResponseInterceptor {

    public static final String BOUNDARY_PARAM_NAME = "boundary";

    private static final String DEFAULT_MDN_MESSAGE_TEMPLATE = "MDN for -\n"
                                                               + " Message ID: $requestHeaders[\"Message-Id\"]\n"
                                                               + "  Subject: $requestHeaders[\"Subject\"]\n"
                                                               + "  Date: $requestHeaders[\"Date\"]\n"
                                                               + "  From: $requestHeaders[\"AS2-From\"]\n"
                                                               + "  To: $requestHeaders[\"AS2-To\"]\n"
                                                               + "  Received on: $responseHeaders[\"Date\"]\n"
                                                               + " Status: $dispositionType \n";

    private static final Logger LOG = LoggerFactory.getLogger(ResponseMDN.class);

    private final String as2Version;
    private final String serverFQDN;
    private final AS2SignatureAlgorithm signingAlgorithm;
    private final Certificate[] signingCertificateChain;
    private final PrivateKey signingPrivateKey;
    private final PrivateKey decryptingPrivateKey;
    private final String mdnMessageTemplate;
    private final Certificate[] validateSigningCertificateChain;

    private VelocityEngine velocityEngine;

    public ResponseMDN(String as2Version, String serverFQDN, AS2SignatureAlgorithm signingAlgorithm,
                       Certificate[] signingCertificateChain, PrivateKey signingPrivateKey, PrivateKey decryptingPrivateKey,
                       String mdnMessageTemplate, Certificate[] validateSigningCertificateChain) {
        this.as2Version = as2Version;
        this.serverFQDN = serverFQDN;
        this.signingAlgorithm = signingAlgorithm;
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
        this.decryptingPrivateKey = decryptingPrivateKey;
        // MDN response is to be sent anyway, so empty or null value will be treated as if
        // the user doesn't know how to compose their own template and/or is satisfied with default one.
        if (!StringUtils.isBlank(mdnMessageTemplate)) {
            this.mdnMessageTemplate = mdnMessageTemplate;
        } else {
            this.mdnMessageTemplate = DEFAULT_MDN_MESSAGE_TEMPLATE;
        }
        this.validateSigningCertificateChain = validateSigningCertificateChain;
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

        HttpRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpRequest.class);
        if (request == null || !(request instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest)) {
            // Not an enclosing request so nothing to do.
            return;
        }

        LOG.debug("Processing MDN for request: {}", httpEntityEnclosingRequest);

        if (HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.DISPOSITION_NOTIFICATION_TO) == null) {
            // no receipt requested by sender
            LOG.debug("MDN not returned: no receipt requested");
            return;
        }

        // Return a Message Disposition Notification Receipt in response body
        String boundary = EntityUtils.createBoundaryValue();
        DispositionNotificationMultipartReportEntity multipartReportEntity;
        if (AS2DispositionType.FAILED.getType()
                .equals(HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_TYPE))) {
            // Return a failed Message Disposition Notification Receipt in response body
            String mdnMessage = createMdnDescription(httpEntityEnclosingRequest, response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.FAILED, null, null, null, null, null, mdnMessageTemplate);
            multipartReportEntity = new DispositionNotificationMultipartReportEntity(
                    httpEntityEnclosingRequest, response, DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.FAILED, null, null, null, null, null, StandardCharsets.US_ASCII.name(), boundary, true,
                    decryptingPrivateKey, mdnMessage, validateSigningCertificateChain);
        } else {
            String mdnMessage = createMdnDescription(httpEntityEnclosingRequest, response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.PROCESSED, null, null, null, null, null,
                    mdnMessageTemplate);
            multipartReportEntity = new DispositionNotificationMultipartReportEntity(
                    httpEntityEnclosingRequest, response, DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.PROCESSED, null, null, null, null, null, StandardCharsets.US_ASCII.name(), boundary,
                    true,
                    decryptingPrivateKey, mdnMessage, validateSigningCertificateChain);
        }

        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser
                .parseDispositionNotificationOptions(
                        HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS),
                        null);

        String receiptAddress = HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.RECEIPT_DELIVERY_OPTION);
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
            String subject = HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.SUBJECT);
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
            String as2From = HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.AS2_TO);
            try {
                AS2Utils.validateAS2Name(as2From);
            } catch (InvalidAS2NameException e) {
                throw new HttpException("Invalid AS-From name", e);
            }
            response.addHeader(AS2Header.AS2_FROM, as2From);

            /* AS2-To header */
            String as2To = HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.AS2_FROM);
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
                    MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(
                            multipartReportEntity, gen,
                            StandardCharsets.US_ASCII.name(), AS2TransferEncoding.BASE64, false, null);
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

        if (LOG.isDebugEnabled()) {
            LOG.debug(AS2Utils.printMessage(response));
        }
    }

    private String createMdnDescription(
            HttpEntityEnclosingRequest request,
            HttpResponse response,
            DispositionMode dispositionMode,
            AS2DispositionType dispositionType,
            AS2DispositionModifier dispositionModifier,
            String[] failureFields,
            String[] errorFields,
            String[] warningFields,
            Map<String, String> extensionFields,
            String mdnMessageTemplate)
            throws HttpException {

        try {
            Context context = new VelocityContext();
            context.put("request", request);
            Map<String, Object> requestHeaders = new HashMap<>();
            for (Header header : request.getAllHeaders()) {
                requestHeaders.put(header.getName(), header.getValue());
            }
            context.put("requestHeaders", requestHeaders);

            Map<String, Object> responseHeaders = new HashMap<>();
            for (Header header : response.getAllHeaders()) {
                responseHeaders.put(header.getName(), header.getValue());
            }
            context.put("responseHeaders", responseHeaders);

            context.put("dispositionMode", dispositionMode);
            context.put("dispositionType", dispositionType);
            context.put("dispositionModifier", dispositionModifier);
            context.put("failureFields", failureFields);
            context.put("errorFields", errorFields);
            context.put("warningFields", warningFields);
            context.put("extensionFields", extensionFields);

            VelocityEngine engine = getVelocityEngine();
            StringWriter buffer = new StringWriter();
            engine.evaluate(context, buffer, getClass().getName(), mdnMessageTemplate);
            return buffer.toString();

        } catch (Exception e) {
            throw new HttpException("failed to create MDN description", e);
        }
    }

    private synchronized VelocityEngine getVelocityEngine() {
        if (velocityEngine == null) {
            velocityEngine = new VelocityEngine();

            // set default properties
            Properties properties = new Properties();
            properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "file, class");
            properties.setProperty("class.resource.loader.description", "Camel Velocity Classpath Resource Loader");
            properties.setProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());
            final Logger velocityLogger = LoggerFactory.getLogger("org.apache.camel.maven.Velocity");
            properties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, velocityLogger.getName());

            velocityEngine.init(properties);
        }
        return velocityEngine;
    }

}
