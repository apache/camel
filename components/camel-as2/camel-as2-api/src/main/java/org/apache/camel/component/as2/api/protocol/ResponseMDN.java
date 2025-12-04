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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.component.as2.api.AS2AsynchronousMDNManager;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2ServerConnection;
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
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseMDN implements HttpResponseInterceptor {

    public static final String BOUNDARY_PARAM_NAME = "boundary";

    public static final String DISPOSITION_TYPE = "Disposition-Type";

    public static final String DISPOSITION_MODIFIER = "Disposition-Modifier";

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
    private final String mdnMessageTemplate;

    private AS2SignatureAlgorithm signingAlgorithm;
    private Certificate[] signingCertificateChain;
    private PrivateKey signingPrivateKey;
    private PrivateKey decryptingPrivateKey;
    private Certificate[] validateSigningCertificateChain;
    private boolean keysAreDynamic =
            false; // Flag indicating if security keys/certs must be dynamically fetched from the HttpContext

    private final Lock lock = new ReentrantLock();
    private VelocityEngine velocityEngine;

    public ResponseMDN(String as2Version, String serverFQDN, String mdnMessageTemplate) {
        this.as2Version = as2Version;
        this.serverFQDN = serverFQDN;
        if (!StringUtils.isBlank(mdnMessageTemplate)) {
            this.mdnMessageTemplate = mdnMessageTemplate;
        } else {
            this.mdnMessageTemplate = DEFAULT_MDN_MESSAGE_TEMPLATE;
        }
        this.keysAreDynamic = true;
    }

    public ResponseMDN(
            String as2Version,
            String serverFQDN,
            AS2SignatureAlgorithm signingAlgorithm,
            Certificate[] signingCertificateChain,
            PrivateKey signingPrivateKey,
            PrivateKey decryptingPrivateKey,
            String mdnMessageTemplate,
            Certificate[] validateSigningCertificateChain) {
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
    public void process(HttpResponse response, EntityDetails entity, HttpContext context)
            throws HttpException, IOException {

        int statusCode = response.getCode();
        if (statusCode < 200 || statusCode >= 300) {
            // RFC4130 - 7.6 - Status codes in the 200 range SHOULD also be used when an entity is returned
            // (a signed receipt in a multipart/signed content type or an unsigned
            // receipt in a multipart/report)
            LOG.debug("MDN not return due to response status code: {}", statusCode);
            return;
        }

        if (this.keysAreDynamic) {
            // Dynamically load path-specific security material from the HttpContext,
            // which was populated by AS2ConsumerConfigInterceptor.
            this.signingAlgorithm =
                    (AS2SignatureAlgorithm) context.getAttribute(AS2ServerConnection.AS2_SIGNING_ALGORITHM);
            this.signingCertificateChain =
                    (Certificate[]) context.getAttribute(AS2ServerConnection.AS2_SIGNING_CERTIFICATE_CHAIN);
            this.signingPrivateKey = (PrivateKey) context.getAttribute(AS2ServerConnection.AS2_SIGNING_PRIVATE_KEY);
            this.decryptingPrivateKey =
                    (PrivateKey) context.getAttribute(AS2ServerConnection.AS2_DECRYPTING_PRIVATE_KEY);
            this.validateSigningCertificateChain =
                    (Certificate[]) context.getAttribute(AS2ServerConnection.AS2_VALIDATE_SIGNING_CERTIFICATE_CHAIN);
        }

        HttpCoreContext coreContext = HttpCoreContext.adapt(context);

        HttpRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpRequest.class);
        if (request == null || !(request instanceof ClassicHttpRequest httpEntityEnclosingRequest)) {
            // Not an enclosing request so nothing to do.
            return;
        }

        LOG.debug("Processing MDN for request: {}", httpEntityEnclosingRequest);

        if (HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.DISPOSITION_NOTIFICATION_TO)
                == null) {
            // no receipt requested by sender
            LOG.debug("MDN not returned: no receipt requested");
            return;
        }

        // Return a Message Disposition Notification Receipt in response body
        String boundary = EntityUtils.createBoundaryValue();
        DispositionNotificationMultipartReportEntity multipartReportEntity;
        if (AS2DispositionType.FAILED.equals(coreContext.getAttribute(DISPOSITION_TYPE, AS2DispositionType.class))) {
            // Return a failed Message Disposition Notification Receipt in response body
            String mdnMessage = createMdnDescription(
                    httpEntityEnclosingRequest,
                    response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.FAILED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mdnMessageTemplate);
            multipartReportEntity = new DispositionNotificationMultipartReportEntity(
                    httpEntityEnclosingRequest,
                    response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.FAILED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    StandardCharsets.US_ASCII.name(),
                    boundary,
                    true,
                    decryptingPrivateKey,
                    mdnMessage,
                    validateSigningCertificateChain);
        } else {
            AS2DispositionModifier dispositionModifier =
                    coreContext.getAttribute(DISPOSITION_MODIFIER, AS2DispositionModifier.class);
            String mdnMessage = createMdnDescription(
                    httpEntityEnclosingRequest,
                    response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.PROCESSED,
                    dispositionModifier,
                    null,
                    null,
                    null,
                    null,
                    mdnMessageTemplate);
            multipartReportEntity = new DispositionNotificationMultipartReportEntity(
                    httpEntityEnclosingRequest,
                    response,
                    DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY,
                    AS2DispositionType.PROCESSED,
                    dispositionModifier,
                    null,
                    null,
                    null,
                    null,
                    StandardCharsets.US_ASCII.name(),
                    boundary,
                    true,
                    decryptingPrivateKey,
                    mdnMessage,
                    validateSigningCertificateChain);
        }

        String receiptAddress =
                HttpMessageUtils.getHeaderValue(httpEntityEnclosingRequest, AS2Header.RECEIPT_DELIVERY_OPTION);
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

            AS2SignedDataGenerator gen = createSigningGenerator(
                    httpEntityEnclosingRequest, signingAlgorithm, signingCertificateChain, signingPrivateKey);

            if (gen != null) {
                // Create signed receipt
                try {
                    MultipartSignedEntity multipartSignedEntity = prepareSignedReceipt(gen, multipartReportEntity);
                    response.setHeader(AS2Header.CONTENT_TYPE, multipartSignedEntity.getContentType());
                    EntityUtils.setMessageEntity(response, multipartSignedEntity);
                } catch (Exception e) {
                    LOG.warn("failed to sign receipt");
                }
            } else {
                // Create unsigned receipt
                response.setHeader(AS2Header.CONTENT_TYPE, multipartReportEntity.getContentType());
                EntityUtils.setMessageEntity(response, multipartReportEntity);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(AS2Utils.printMessage(response));
        }
    }

    // may be created for sync or async MDN messages
    public static AS2SignedDataGenerator createSigningGenerator(
            HttpRequest request,
            AS2SignatureAlgorithm signingAlgorithm,
            Certificate[] signingCertificateChain,
            PrivateKey signingPrivateKey)
            throws HttpException {
        DispositionNotificationOptions dispositionNotificationOptions =
                DispositionNotificationOptionsParser.parseDispositionNotificationOptions(
                        HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS), null);

        AS2SignedDataGenerator gen = null;
        if (dispositionNotificationOptions.getSignedReceiptProtocol() != null
                && signingCertificateChain != null
                && signingPrivateKey != null) {
            gen = SigningUtils.createSigningGenerator(signingAlgorithm, signingCertificateChain, signingPrivateKey);
        }
        return gen;
    }

    // signs an MDN for sync or async receipts
    public static MultipartSignedEntity prepareSignedReceipt(
            AS2SignedDataGenerator gen, DispositionNotificationMultipartReportEntity multipartReportEntity)
            throws HttpException {
        multipartReportEntity.setMainBody(false);
        return new MultipartSignedEntity(
                multipartReportEntity, gen, StandardCharsets.US_ASCII.name(), AS2TransferEncoding.BASE64, false, null);
    }

    private String createMdnDescription(
            ClassicHttpRequest request,
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
            for (Header header : request.getHeaders()) {
                requestHeaders.put(header.getName(), header.getValue());
            }
            context.put("requestHeaders", requestHeaders);

            Map<String, Object> responseHeaders = new HashMap<>();
            for (Header header : response.getHeaders()) {
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

    private VelocityEngine getVelocityEngine() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }
}
