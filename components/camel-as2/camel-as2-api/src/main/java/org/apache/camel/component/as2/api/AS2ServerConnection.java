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
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.io.AS2BHttpServerConnection;
import org.apache.camel.component.as2.api.protocol.ResponseMDN;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2ServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ServerConnection.class);

    private static final String REQUEST_LISTENER_THREAD_NAME_PREFIX = "AS2Svr-";
    private static final String REQUEST_HANDLER_THREAD_NAME_PREFIX = "AS2Hdlr-";

    public static final String AS2_DECRYPTING_PRIVATE_KEY = "AS2_DECRYPTING_PRIVATE_KEY";
    public static final String AS2_VALIDATE_SIGNING_CERTIFICATE_CHAIN = "AS2_VALIDATE_SIGNING_CERTIFICATE_CHAIN";
    public static final String AS2_SIGNING_PRIVATE_KEY = "AS2_SIGNING_PRIVATE_KEY";
    public static final String AS2_SIGNING_CERTIFICATE_CHAIN = "AS2_SIGNING_CERTIFICATE_CHAIN";
    public static final String AS2_SIGNING_ALGORITHM = "AS2_SIGNING_ALGORITHM";

    private ServerSocket serversocket;
    private RequestListenerService listenerService;
    private RequestAcceptorThread acceptorThread;
    private final Lock lock = new ReentrantLock();

    private final String as2Version;
    private final String originServer;
    private final String serverFqdn;
    private final String userName;
    private final String password;
    private final String accessToken;

    /**
     * Stores the configuration for each consumer endpoint path (e.g., "/consumerA")
     */
    private final Map<String, AS2ConsumerConfiguration> consumerConfigurations = new ConcurrentHashMap<>();

    /**
     * Simple wrapper class to associate the AS2ConsumerConfiguration with the specific request URI path that was
     * matched. Used exclusively by the ThreadLocal state.
     */
    private static class ThreadLocalConfigWrapper {
        final AS2ConsumerConfiguration config;
        final String requestUriPath;

        ThreadLocalConfigWrapper(AS2ConsumerConfiguration config, String requestUriPath) {
            this.config = config;
            this.requestUriPath = requestUriPath;
        }
    }

    /**
     * Stores the request-specific AS2ConsumerConfiguration and path. Used for post-processing logic (like asynchronous
     * MDN) after the main HttpService handling is complete.
     */
    private static final ThreadLocal<ThreadLocalConfigWrapper> CURRENT_CONSUMER_CONFIG = new ThreadLocal<>();

    /**
     * Configuration data holding all necessary security material (signing keys/certs and decryption keys/certs) for a
     * single AS2 consumer endpoint. This immutable object is looked up per request URI.
     */
    public static class AS2ConsumerConfiguration {
        private final Certificate[] signingCertificateChain;
        private final PrivateKey signingPrivateKey;
        private final PrivateKey decryptingPrivateKey;
        private final Certificate[] validateSigningCertificateChain;
        private final AS2SignatureAlgorithm signingAlgorithm;

        public AS2ConsumerConfiguration(
                AS2SignatureAlgorithm signingAlgorithm,
                Certificate[] signingCertificateChain,
                PrivateKey signingPrivateKey,
                PrivateKey decryptingPrivateKey,
                Certificate[] validateSigningCertificateChain) {
            this.signingAlgorithm = signingAlgorithm;
            this.signingCertificateChain = signingCertificateChain;
            this.signingPrivateKey = signingPrivateKey;
            this.decryptingPrivateKey = decryptingPrivateKey;
            this.validateSigningCertificateChain = validateSigningCertificateChain;
        }

        // Getters
        public Certificate[] getValidateSigningCertificateChain() {
            return validateSigningCertificateChain;
        }

        public Certificate[] getSigningCertificateChain() {
            return signingCertificateChain;
        }

        public AS2SignatureAlgorithm getSigningAlgorithm() {
            return signingAlgorithm;
        }

        public PrivateKey getSigningPrivateKey() {
            return signingPrivateKey;
        }

        public PrivateKey getDecryptingPrivateKey() {
            return decryptingPrivateKey;
        }
    }

    /**
     * Retrieves the specific AS2 consumer configuration associated with the given request path.
     *
     * @param  path The canonical request URI path (e.g., "/consumerA").
     * @return      An Optional containing the configuration if a match is found, otherwise empty.
     */
    public Optional<AS2ConsumerConfiguration> getConfigurationForPath(String path) {
        return Optional.ofNullable(consumerConfigurations.get(path));
    }

    /**
     * Dynamically determines and injects the AS2 security configuration (keys, certificates, and algorithm) for the
     * incoming HTTP request.
     *
     * This method performs three main tasks: 1. Looks up the correct AS2ConsumerConfiguration based on the request URI
     * path. 2. Injects the decryption and signing security material into the HttpContext for use by downstream
     * processors (like the AS2Consumer and ResponseMDN). 3. Stores the configuration in a ThreadLocal for use by
     * asynchronous MDN logic.
     *
     * @param  request The incoming HTTP request.
     * @param  context The shared execution context for the request lifecycle.
     * @return         The AS2ConsumerConfiguration object found, or null if none was matched.
     */
    private AS2ConsumerConfiguration setupConfigurationForRequest(ClassicHttpRequest request, HttpContext context) {
        String requestUri = request.getRequestUri();
        String requestUriPath = cleanUpPath(requestUri);

        // 1. LOOKUP: Find the specific consumer configuration
        AS2ConsumerConfiguration config =
                AS2ServerConnection.this.getConfigurationForPath(requestUriPath).orElse(null);

        // 2. Logging BEFORE injection (CRITICAL for debugging path issues)
        LOG.debug(
                "Processing request. Incoming URI: {}, Canonical Path: {}. Config Found: {}",
                requestUri,
                requestUriPath,
                (config != null));

        // 3. Handle missing config
        if (config == null) {
            LOG.warn(
                    "No AS2 consumer configuration found for canonical path: {}. Encrypted messages will likely fail.",
                    requestUriPath);
            return null;
        }

        // 4. INJECTION: Inject dynamic security keys into the HttpContext
        context.setAttribute(AS2_DECRYPTING_PRIVATE_KEY, config.getDecryptingPrivateKey());
        context.setAttribute(AS2_VALIDATE_SIGNING_CERTIFICATE_CHAIN, config.getValidateSigningCertificateChain());
        context.setAttribute(AS2_SIGNING_PRIVATE_KEY, config.getSigningPrivateKey());
        context.setAttribute(AS2_SIGNING_CERTIFICATE_CHAIN, config.getSigningCertificateChain());
        context.setAttribute(AS2_SIGNING_ALGORITHM, config.getSigningAlgorithm());

        // 5. CRITICAL READ-BACK CHECK: Immediately check if the key is retrievable from the context
        Object checkKey = context.getAttribute(AS2_DECRYPTING_PRIVATE_KEY);

        if (checkKey == null) {
            LOG.error(
                    "FATAL: Decrypting Private Key failed to be read back from HttpContext immediately after injection for path: {}",
                    requestUriPath);
        } else if (!(checkKey instanceof PrivateKey)) {
            LOG.error(
                    "FATAL: Key in HttpContext is not a PrivateKey object! Found type: {}",
                    checkKey.getClass().getName());
        } else {
            LOG.debug(
                    "Context injection confirmed: Decrypting Key set successfully into HttpContext. Key type: {}",
                    checkKey.getClass().getName());
        }

        // 6. Set ThreadLocal for later MDN processing
        ThreadLocalConfigWrapper wrapper = new ThreadLocalConfigWrapper(config, requestUriPath);
        CURRENT_CONSUMER_CONFIG.set(wrapper);

        return config;
    }

    /**
     * Extracts and normalizes the path component from the request URI.
     *
     * This ensures consistency by stripping query parameters and scheme/authority, and defaults to "/" if the path is
     * empty or parsing fails.
     *
     * @param  requestUri The full request URI string from the HTTP request line.
     * @return            The canonical path, starting with a "/", without query parameters.
     */
    private String cleanUpPath(String requestUri) {
        try {
            URI uri = new URI(requestUri);
            String path = uri.getPath();
            // Ensure path is not null and normalize to "/" if it is empty/null after parsing
            if (path == null || path.isEmpty()) {
                return "/";
            }
            return path;
        } catch (Exception e) {
            // Should not happen for a valid HTTP request line
            LOG.warn("Error parsing request URI: {}", requestUri, e);
            return "/"; // Default to root path in case of error
        }
    }

    /**
     * Interceptor that executes early in the request processing chain to find the correct
     * {@link AS2ConsumerConfiguration} for the incoming request URI and injects its security material
     * (keys/certs/algorithm) into the {@link HttpContext} and {@link ThreadLocal} storage.
     */
    private class AS2ConsumerConfigInterceptor implements HttpRequestInterceptor {

        @Override
        public void process(HttpRequest request, EntityDetails entityDetails, HttpContext context)
                throws HttpException, IOException {
            if (request instanceof ClassicHttpRequest) {
                // Now safely calling the method on the outer class instance (AS2ServerConnection.this)
                AS2ServerConnection.this.setupConfigurationForRequest((ClassicHttpRequest) request, context);
            }
        }
    }

    class RequestListenerService {

        private final HttpService httpService;
        private final RequestHandlerRegistry registry;

        public RequestListenerService(
                String as2Version, String originServer, String serverFqdn, String mdnMessageTemplate)
                throws IOException {

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc =
                    initProtocolProcessor(as2Version, originServer, serverFqdn, mdnMessageTemplate);

            registry = new RequestHandlerRegistry<>();
            HttpServerRequestHandler handler = new BasicHttpServerRequestHandler(registry);

            // Set up the HTTP service
            httpService = new HttpService(inhttpproc, handler);
        }

        void registerHandler(String requestUriPattern, HttpRequestHandler httpRequestHandler) {
            registry.register(null, requestUriPattern, httpRequestHandler);
        }

        void unregisterHandler(String requestUriPattern) {
            // we cannot remove from http registry, but we can replace with a not found to simulate 404
            registry.register(null, requestUriPattern, new NotFoundHttpRequestHandler());
        }
    }

    class RequestAcceptorThread extends Thread {

        private final RequestListenerService service;

        public RequestAcceptorThread(int port, SSLContext sslContext, RequestListenerService service)
                throws IOException {
            setName(REQUEST_LISTENER_THREAD_NAME_PREFIX + port);
            this.service = service;

            if (sslContext == null) {
                serversocket = new ServerSocket(port);
            } else {
                SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
                serversocket = factory.createServerSocket(port);
            }
        }

        @Override
        public void run() {
            // serversocket is now a field of the outer AS2ServerConnection class
            LOG.info("Listening on port {}", serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {

                    // Set up incoming HTTP connection
                    final Socket inSocket = serversocket.accept();

                    // Start worker thread, using the service's HttpService
                    final Thread t = new RequestHandlerThread(this.service.httpService, inSocket);
                    t.setDaemon(true);
                    t.start();
                } catch (final InterruptedIOException | SocketException ex) {
                    // If interrupted or server socket closed
                    break;
                } catch (final IOException e) {
                    LOG.error("I/O error initialising connection thread: {}", e.getMessage());
                    break;
                }
            }
        }
    }

    class RequestHandlerThread extends Thread {
        private final HttpService httpService;
        private final HttpServerConnection serverConnection;

        public RequestHandlerThread(HttpService httpService, Socket inSocket) throws IOException {
            final int bufSize = 8 * 1024;
            Http1Config cfg = Http1Config.custom().setBufferSize(bufSize).build();
            final AS2BHttpServerConnection inConn = new AS2BHttpServerConnection(cfg);
            LOG.info("Incoming connection from {}", inSocket.getInetAddress());
            inConn.bind(inSocket);

            // TODO Update once baseline is Java 21
            // setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + threadId());
            setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + getId());

            this.httpService = httpService;
            this.serverConnection = inConn;
        }

        @Override
        public void run() {
            LOG.info("Processing new AS2 request");
            final HttpContext context = new BasicHttpContext(null);

            try {
                while (!Thread.interrupted()) {

                    this.httpService.handleRequest(this.serverConnection, context);

                    HttpCoreContext coreContext = HttpCoreContext.adapt(context);

                    // Safely retrieve the AS2 consumer configuration and path from ThreadLocal storage.
                    AS2ConsumerConfiguration config = Optional.ofNullable(CURRENT_CONSUMER_CONFIG.get())
                            .map(w -> w.config)
                            .orElse(null);

                    String recipientAddress =
                            coreContext.getAttribute(AS2AsynchronousMDNManager.RECIPIENT_ADDRESS, String.class);

                    if (recipientAddress != null && config != null) {
                        // Send the MDN asynchronously.

                        DispositionNotificationMultipartReportEntity multipartReportEntity = coreContext.getAttribute(
                                AS2AsynchronousMDNManager.ASYNCHRONOUS_MDN,
                                DispositionNotificationMultipartReportEntity.class);
                        AS2AsynchronousMDNManager asynchronousMDNManager = new AS2AsynchronousMDNManager(
                                AS2ServerConnection.this.as2Version,
                                AS2ServerConnection.this.originServer,
                                AS2ServerConnection.this.serverFqdn,
                                config.getSigningCertificateChain(),
                                config.getSigningPrivateKey(),
                                AS2ServerConnection.this.userName,
                                AS2ServerConnection.this.password,
                                AS2ServerConnection.this.accessToken);

                        HttpRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpRequest.class);
                        AS2SignedDataGenerator gen = ResponseMDN.createSigningGenerator(
                                request,
                                config.getSigningAlgorithm(),
                                config.getSigningCertificateChain(),
                                config.getSigningPrivateKey());

                        if (gen != null) {
                            // send a signed MDN
                            MultipartSignedEntity multipartSignedEntity = null;
                            try {
                                multipartSignedEntity = ResponseMDN.prepareSignedReceipt(gen, multipartReportEntity);
                            } catch (Exception e) {
                                LOG.warn("failed to sign MDN");
                            }
                            if (multipartSignedEntity != null) {
                                asynchronousMDNManager.send(
                                        multipartSignedEntity,
                                        multipartSignedEntity.getContentType(),
                                        recipientAddress);
                            }
                        } else {
                            // send an unsigned MDN
                            asynchronousMDNManager.send(
                                    multipartReportEntity,
                                    multipartReportEntity.getMainMessageContentType(),
                                    recipientAddress);
                        }
                    }
                }
            } catch (final ConnectionClosedException ex) {
                LOG.info("Client closed connection");
            } catch (final IOException ex) {
                LOG.error("I/O error: {}", ex.getMessage());
            } catch (final HttpException ex) {
                LOG.error("Unrecoverable HTTP protocol violation: {}", ex.getMessage(), ex);
            } finally {
                try {
                    this.serverConnection.close();
                } catch (final IOException ignore) {
                }
            }
        }
    }

    public AS2ServerConnection(
            String as2Version,
            String originServer,
            String serverFqdn,
            Integer serverPortNumber,
            AS2SignatureAlgorithm signingAlgorithm,
            Certificate[] signingCertificateChain,
            PrivateKey signingPrivateKey,
            PrivateKey decryptingPrivateKey,
            String mdnMessageTemplate,
            Certificate[] validateSigningCertificateChain,
            SSLContext sslContext,
            String userName,
            String password,
            String accessToken)
            throws IOException {
        this.as2Version = ObjectHelper.notNull(as2Version, "as2Version");
        this.originServer = ObjectHelper.notNull(originServer, "userAgent");
        this.serverFqdn = ObjectHelper.notNull(serverFqdn, "serverFqdn");
        final Integer parserServerPortNumber = ObjectHelper.notNull(serverPortNumber, "serverPortNumber");
        this.userName = userName;
        this.password = password;
        this.accessToken = accessToken;

        // Create and register a default consumer configuration for the root path ('/').
        // This ensures that all incoming requests have a fallback configuration for decryption
        // and MDN signing, even if they don't match a specific Camel route path.
        AS2ServerConnection.AS2ConsumerConfiguration consumerConfig = new AS2ServerConnection.AS2ConsumerConfiguration(
                signingAlgorithm,
                signingCertificateChain,
                signingPrivateKey,
                decryptingPrivateKey,
                validateSigningCertificateChain);
        registerConsumerConfiguration("/", consumerConfig);

        listenerService =
                new RequestListenerService(this.as2Version, this.originServer, this.serverFqdn, mdnMessageTemplate);

        acceptorThread = new RequestAcceptorThread(parserServerPortNumber, sslContext, listenerService);
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    public Certificate[] getValidateSigningCertificateChain() {
        return Optional.ofNullable(CURRENT_CONSUMER_CONFIG.get())
                .map(w -> w.config.getValidateSigningCertificateChain())
                .orElse(null);
    }

    public PrivateKey getSigningPrivateKey() {
        return Optional.ofNullable(CURRENT_CONSUMER_CONFIG.get())
                .map(w -> w.config.getSigningPrivateKey())
                .orElse(null);
    }

    public PrivateKey getDecryptingPrivateKey() {
        return Optional.ofNullable(CURRENT_CONSUMER_CONFIG.get())
                .map(w -> w.config.getDecryptingPrivateKey())
                .orElse(null);
    }

    public void registerConsumerConfiguration(String path, AS2ConsumerConfiguration config) {
        consumerConfigurations.put(path, config);
    }

    public void close() {
        if (acceptorThread != null) {
            lock.lock();
            try {
                try {
                    // 3. Close the shared ServerSocket
                    if (serversocket != null) {
                        serversocket.close();
                    }
                } catch (IOException e) {
                    LOG.debug(e.getMessage(), e);
                } finally {
                    acceptorThread = null;
                    listenerService = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public void listen(String requestUri, HttpRequestHandler handler) {
        if (listenerService != null) {
            lock.lock();
            try {
                listenerService.registerHandler(requestUri, handler);
            } finally {
                lock.unlock();
            }
        }
    }

    public void unlisten(String requestUri) {
        if (listenerService != null) {
            lock.lock();
            try {
                listenerService.unregisterHandler(requestUri);
                consumerConfigurations.remove(requestUri);
            } finally {
                lock.unlock();
            }
        }
    }

    protected HttpProcessor initProtocolProcessor(
            String as2Version, String originServer, String serverFqdn, String mdnMessageTemplate) {
        return HttpProcessorBuilder.create()
                .addFirst(
                        new AS2ConsumerConfigInterceptor()) // Sets up the request-specific keys and certificates in the
                // HttpContext
                .add(new ResponseContent(true))
                .add(new ResponseServer(originServer))
                .add(new ResponseDate())
                .add(new ResponseConnControl())
                .add(new ResponseMDN(as2Version, serverFqdn, mdnMessageTemplate))
                .build();
    }
}
