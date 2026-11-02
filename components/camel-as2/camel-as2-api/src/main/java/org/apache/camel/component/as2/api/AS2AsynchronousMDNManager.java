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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

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
    private SSLContext sslContext;
    private boolean requireHttps;

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
        this(as2Version, userAgent, senderFQDN, signingCertificateChain, signingPrivateKey, userName, password,
             accessToken, allowedHosts, null);
    }

    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     String userName,
                                     String password,
                                     String accessToken,
                                     String allowedHosts,
                                     SSLContext sslContext) {
        this(as2Version, userAgent, senderFQDN, signingCertificateChain, signingPrivateKey, userName, password,
             accessToken, allowedHosts, sslContext, false);
    }

    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     String userName,
                                     String password,
                                     String accessToken,
                                     String allowedHosts,
                                     SSLContext sslContext,
                                     boolean requireHttps) {
        this.allowedHosts = allowedHosts;
        this.sslContext = sslContext;
        this.requireHttps = requireHttps;
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
        // The delivery scheme is sender-chosen (untrusted input). Plain http is allowed unless asyncMdnRequireHttps
        // is set; https is allowed only when the AS2 endpoint has SSLContextParameters configured, otherwise it is
        // refused (fail-closed) rather than written in cleartext to the TLS port. Any other scheme is rejected.
        boolean https = "https".equals(scheme);
        if (!"http".equals(scheme) && !https) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN: the delivery address must use http or https");
        }
        // The scheme is sender-chosen. When the operator requires https (asyncMdnRequireHttps), refuse a non-https
        // delivery address before the connection is opened and before the credentials are attached, so they are never
        // written in cleartext to a sender-chosen destination.
        if (requireHttps && !https) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN: asyncMdnRequireHttps is set but the delivery address"
                                    + " does not use https");
        }
        if (https && sslContext == null) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN over https: no SSLContextParameters are configured"
                                    + " on the AS2 endpoint");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null) {
            throw new HttpException("Refusing to deliver the asynchronous MDN: the delivery address has no host");
        }
        int defaultPort = https ? 443 : 80;
        int port = uri.getPort() != -1 ? uri.getPort() : defaultPort;

        // Match the full authority - host and port - of the delivery address against the allow-list, not the host
        // alone, so a listed host does not authorise every port on that host.
        boolean authorityIsAllowed = isAllowedAuthority(host, port, defaultPort);
        if (allowedHosts != null && !allowedHosts.isBlank() && !authorityIsAllowed) {
            throw new HttpException(
                    "Refusing to deliver the asynchronous MDN: the delivery address is not in asyncMdnAllowedHosts");
        }

        int buffSize = 8 * 1024;

        Http1Config h1Config = Http1Config.custom().setBufferSize(buffSize).build();
        HttpConnectionFactory<ManagedHttpClientConnection> connFactory
                = ManagedHttpClientConnectionFactory.builder().http1Config(h1Config).build();

        try (HttpClientConnection httpConnection = connFactory.createConnection(createSocket(https, host, port))) {

            // Add Context attributes
            HttpCoreContext httpContext = HttpCoreContext.create();
            httpContext.setAttribute(RECIPIENT_ADDRESS, recipientDeliveryAddress);

            ClassicHttpRequest request = new BasicClassicHttpRequest("POST", uri);
            request.setHeader(AS2Header.CONTENT_TYPE, contentType);
            // Credentials are only attached to an authority - host and port - the operator has vouched for. Without an
            // allow-list the destination is entirely sender-chosen, so the MDN is still delivered but without them.
            if (authorityIsAllowed) {
                AS2HeaderUtils.addAuthorizationHeader(request, userName, password, accessToken);
            } else if (userName != null || accessToken != null) {
                LOG.warn("Asynchronous MDN credentials not sent to sender-supplied authority {}:"
                         + " set asyncMdnAllowedHosts to authorise it",
                        formatAuthority(host, port));
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

    private Socket createSocket(boolean https, String host, int port) throws IOException {
        if (!https) {
            return new Socket(host, port);
        }
        SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(host, port);
        try {
            // verify the delivery host against the certificate the peer presents during the handshake
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParameters);
            // handshake now so a certificate or hostname mismatch fails before the MDN and any credentials are written
            sslSocket.startHandshake();
        } catch (IOException e) {
            sslSocket.close();
            throw e;
        }
        return sslSocket;
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

    /**
     * Writes a delivery authority the way an {@code asyncMdnAllowedHosts} entry is written, so a log message names an
     * entry the operator can copy. The host has been through normalizeHost, so an IPv6 literal (the only host that
     * contains a colon) is put back in square brackets ({@code [::1]:8443}): unbracketed, {@code ::1:8443} would be
     * read as another IPv6 address on the default port. Any other host is written as {@code host:port}.
     */
    static String formatAuthority(String host, int port) {
        return host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
    }

    /**
     * Matches the resolved delivery authority - host and port - against {@code asyncMdnAllowedHosts}. Each entry is a
     * host with an optional port ({@code host} or {@code host:port}); a bare-host entry matches only the default port
     * for the scheme ({@code defaultPort}), so a listed host does not authorise every port on that host.
     */
    boolean isAllowedAuthority(String host, int port, int defaultPort) {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return false;
        }
        for (String allowed : allowedHosts.split(",")) {
            String entry = allowed.trim();
            if (!entry.isEmpty() && authorityMatches(entry, host, port, defaultPort)) {
                return true;
            }
        }
        return false;
    }

    private static boolean authorityMatches(String entry, String host, int port, int defaultPort) {
        String entryHost;
        int entryPort;
        if (entry.charAt(0) == '[') {
            // bracketed IPv6 literal, optionally followed by :port
            int close = entry.indexOf(']');
            if (close < 0) {
                return false;
            }
            entryHost = entry.substring(1, close);
            entryPort = defaultPort;
            if (close + 1 < entry.length()) {
                if (entry.charAt(close + 1) != ':') {
                    return false;
                }
                Integer parsed = parsePort(entry.substring(close + 2));
                if (parsed == null) {
                    return false;
                }
                entryPort = parsed;
            }
        } else {
            int colon = entry.indexOf(':');
            if (colon >= 0 && entry.indexOf(':', colon + 1) < 0) {
                // a single colon separates host and port; an unbracketed IPv6 literal has several and keeps the default
                entryHost = entry.substring(0, colon);
                Integer parsed = parsePort(entry.substring(colon + 1));
                if (parsed == null) {
                    return false;
                }
                entryPort = parsed;
            } else {
                entryHost = entry;
                entryPort = defaultPort;
            }
        }
        return entryPort == port && entryHost.equalsIgnoreCase(host);
    }

    private static Integer parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 && parsed <= 65535 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
