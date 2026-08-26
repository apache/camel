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
package org.apache.camel.component.as2.api;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Locale;

import org.apache.camel.component.as2.api.entity.MultipartMimeEntity;
import org.apache.camel.component.as2.api.protocol.RequestAsynchronousMDN;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestDate;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2AsynchronousMDNManager {

    private static final Logger LOG = LoggerFactory.getLogger(AS2AsynchronousMDNManager.class);

    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the AS2 Asynchronous MDN Manager.
     */
    public static final String CAMEL_AS2_ASYNC_MDN_PREFIX = "camel-as2.async-mdn.";

    /**
     * The HTTP Context Attribute containing the HTTP request message transporting the EDI message
     *
     * @deprecated Use getter method from HttpContext implementation.
     */
    @Deprecated
    public static final String HTTP_REQUEST = HttpCoreContext.HTTP_REQUEST;

    /**
     * The HTTP Context Attribute containing the HTTP response message transporting the EDI message
     *
     * @deprecated Use getter method from HttpContext implementation.
     */
    @Deprecated
    public static final String HTTP_RESPONSE = HttpCoreContext.HTTP_RESPONSE;

    /**
     * The HTTP Context Attribute containing the AS2 Connection used to send request message.
     */
    public static final String AS2_CONNECTION = CAMEL_AS2_ASYNC_MDN_PREFIX + "as2-connection";

    /**
     * The HTTP Context Attribute indicating the AS2 name of MDN sender.
     */
    public static final String RECIPIENT_ADDRESS = CAMEL_AS2_ASYNC_MDN_PREFIX + "recipient-address";

    /**
     * The HTTP Context Attribute containing an asynchronous MDN receipt.
     */
    public static final String ASYNCHRONOUS_MDN = CAMEL_AS2_ASYNC_MDN_PREFIX + "asynchronous-mdn";

    private HttpProcessor httpProcessor;

    @SuppressWarnings("unused")
    private Certificate[] signingCertificateChain;
    @SuppressWarnings("unused")
    private PrivateKey signingPrivateKey;
    private String userName;
    private String password;
    private String accessToken;
    private String allowedHosts;

    /**
     * @deprecated use
     *             {@link #AS2AsynchronousMDNManager(String, String, String, Certificate[], PrivateKey, String, String, String, String)}
     *             which also takes the allowed delivery hosts. This constructor delivers the MDN without attaching the
     *             configured credentials, because no allow-list is supplied.
     */
    @Deprecated
    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     String userName,
                                     String password,
                                     String accessToken) {
        this(as2Version, userAgent, senderFQDN, signingCertificateChain, signingPrivateKey, userName, password,
             accessToken, null);
    }

    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     String userName,
                                     String password,
                                     String accessToken,
                                     String allowedHosts) {
        this.allowedHosts = allowedHosts;
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
        this.userName = userName;
        this.password = password;
        this.accessToken = accessToken;

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create().add(new RequestAsynchronousMDN(as2Version, senderFQDN))
                .add(new RequestTargetHost()).add(new RequestUserAgent(userAgent)).add(new RequestDate())
                .add(new RequestContent(true)).add(new RequestConnControl())
                .build();
    }

    // Sends the signed or unsigned AS2-MDN to the URI requested by the sender of the AS2 message.
    public HttpCoreContext send(
            MultipartMimeEntity multipartMimeEntity,
            String contentType,
            String recipientDeliveryAddress)
            throws HttpException {
        ObjectHelper.notNull(multipartMimeEntity, "multipartMimeEntity");
        ObjectHelper.notNull(contentType, "contentType");
        ObjectHelper.notNull(recipientDeliveryAddress, "recipientDeliveryAddress");

        // The delivery address is chosen by the sender of the AS2 message (the Receipt-Delivery-Option
        // header), so it is untrusted input that selects an outbound destination.
        URI uri = URI.create(recipientDeliveryAddress);
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.US);
        // Only http. This class delivers over a plain Socket and has no TLS of any kind, so accepting https
        // would mean writing the request - including the Authorization header - in cleartext to the TLS port.
        // https delivery has never worked here for that reason, so refusing it removes nothing that functioned.
        if (!"http".equals(scheme)) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN: the delivery address must use http."
                                    + " TLS delivery of asynchronous MDNs is not supported");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null) {
            throw new HttpException("Refusing to deliver the asynchronous MDN: the delivery address has no host");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : 80;

        boolean hostIsAllowed = isAllowedHost(host);
        if (allowedHosts != null && !allowedHosts.isBlank() && !hostIsAllowed) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN: the delivery address host is not in asyncMdnAllowedHosts");
        }

        int buffSize = 8 * 1024;

        Http1Config h1Config = Http1Config.custom().setBufferSize(buffSize).build();
        HttpConnectionFactory<ManagedHttpClientConnection> connFactory
                = ManagedHttpClientConnectionFactory.builder().http1Config(h1Config).build();

        try (HttpClientConnection httpConnection = connFactory.createConnection(new Socket(host, port))) {

            // Add Context attributes
            HttpCoreContext httpContext = HttpCoreContext.create();
            httpContext.setAttribute(RECIPIENT_ADDRESS, recipientDeliveryAddress);

            ClassicHttpRequest request = new BasicClassicHttpRequest("POST", uri);
            request.setHeader(AS2Header.CONTENT_TYPE, contentType);
            // Credentials are only attached to a host the operator has vouched for. Without an allow-list the
            // destination is entirely sender-chosen, so the MDN is still delivered but without them.
            if (hostIsAllowed) {
                AS2HeaderUtils.addAuthorizationHeader(request, userName, password, accessToken);
            } else if (userName != null || accessToken != null) {
                LOG.warn("Asynchronous MDN credentials not sent to sender-supplied host {}:"
                         + " set asyncMdnAllowedHosts to authorise it",
                        host);
            }
            httpContext.setRequest(request);
            multipartMimeEntity.setMainBody(true);
            EntityUtils.setMessageEntity(request, multipartMimeEntity);

            HttpResponse response;
            try {
                httpContext.setAttribute(AS2_CONNECTION, httpConnection);
                response = send(httpConnection, request, httpContext);
            } catch (IOException e) {
                throw new HttpException("Failed to send http request message", e);
            }
            httpContext.setResponse(response);

            return httpContext;
        } catch (Exception e) {
            throw new HttpException("failed to send MDN", e);
        }
    }

    /**
     * {@link URI#getHost()} returns an IPv6 literal in its bracketed form ({@code [::1]}), which would never match an
     * allow-list entry written the way an operator writes it. Compare the address itself.
     */
    private static String normalizeHost(String host) {
        if (host != null && host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private boolean isAllowedHost(String host) {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return false;
        }
        for (String allowed : allowedHosts.split(",")) {
            if (allowed.trim().equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    private HttpResponse send(HttpClientConnection httpConnection, ClassicHttpRequest request, HttpCoreContext httpContext)
            throws HttpException, IOException {

        // Execute Request
        HttpRequestExecutor httpExecutor = new HttpRequestExecutor();
        httpExecutor.preProcess(request, httpProcessor, httpContext);
        ClassicHttpResponse response = httpExecutor.execute(request, httpConnection, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        return response;
    }

}
