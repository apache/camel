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
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MultipartMimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.io.AS2BHttpServerConnection;
import org.apache.camel.component.as2.api.protocol.ResponseMDN;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
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

    private ServerSocket serversocket;
    private RequestListenerService listenerService;
    private RequestAcceptorThread acceptorThread;
    private final Lock lock = new ReentrantLock();

    private final String as2Version;
    private final String originServer;
    private final String serverFqdn;
    private final Certificate[] signingCertificateChain;
    private final PrivateKey signingPrivateKey;
    private final PrivateKey decryptingPrivateKey;
    private final Certificate[] validateSigningCertificateChain;
    private final AS2SignatureAlgorithm signingAlgorithm;

    class RequestListenerService {

        private final HttpService httpService;
        private final RequestHandlerRegistry registry;

        public RequestListenerService(String as2Version,
                                      String originServer,
                                      String serverFqdn,
                                      AS2SignatureAlgorithm signatureAlgorithm,
                                      String mdnMessageTemplate,
                                      Certificate[] validateSigningCertificateChain)
                                                                                     throws IOException {

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc = initProtocolProcessor(as2Version, originServer, serverFqdn,
                    null, null, null, null, mdnMessageTemplate,
                    validateSigningCertificateChain);

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

            // 2. BIND THE PORT HERE! This happens only once.
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
                    String recipientAddress = coreContext.getAttribute(AS2AsynchronousMDNManager.RECIPIENT_ADDRESS,
                            String.class);

                    if (recipientAddress != null) {
                        // Send the MDN asynchronously.

                        DispositionNotificationMultipartReportEntity multipartReportEntity = coreContext.getAttribute(
                                AS2AsynchronousMDNManager.ASYNCHRONOUS_MDN,
                                DispositionNotificationMultipartReportEntity.class);
                        AS2AsynchronousMDNManager asynchronousMDNManager = new AS2AsynchronousMDNManager(
                                as2Version,
                                originServer, serverFqdn, signingCertificateChain, signingPrivateKey);

                        HttpRequest request = coreContext.getAttribute(HttpCoreContext.HTTP_REQUEST, HttpRequest.class);
                        AS2SignedDataGenerator gen = ResponseMDN.createSigningGenerator(
                                request, signingAlgorithm, signingCertificateChain, signingPrivateKey);

                        MultipartMimeEntity asyncReceipt = multipartReportEntity;
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
                                        multipartSignedEntity, multipartSignedEntity.getContentType(), recipientAddress);
                            }
                        } else {
                            // send an unsigned MDN
                            asynchronousMDNManager.send(multipartReportEntity,
                                    multipartReportEntity.getMainMessageContentType(), recipientAddress);
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

    public AS2ServerConnection(String as2Version,
                               String originServer,
                               String serverFqdn,
                               Integer serverPortNumber,
                               AS2SignatureAlgorithm signingAlgorithm,
                               Certificate[] signingCertificateChain,
                               PrivateKey signingPrivateKey,
                               PrivateKey decryptingPrivateKey,
                               String mdnMessageTemplate,
                               Certificate[] validateSigningCertificateChain,
                               SSLContext sslContext)
                                                      throws IOException {
        this.as2Version = ObjectHelper.notNull(as2Version, "as2Version");
        this.originServer = ObjectHelper.notNull(originServer, "userAgent");
        this.serverFqdn = ObjectHelper.notNull(serverFqdn, "serverFqdn");
        final Integer parserServerPortNumber = ObjectHelper.notNull(serverPortNumber, "serverPortNumber");
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
        this.decryptingPrivateKey = decryptingPrivateKey;
        this.validateSigningCertificateChain = validateSigningCertificateChain;
        this.signingAlgorithm = signingAlgorithm;

        listenerService = new RequestListenerService(
                this.as2Version, this.originServer, this.serverFqdn,
                signingAlgorithm, mdnMessageTemplate, validateSigningCertificateChain);

        acceptorThread = new RequestAcceptorThread(parserServerPortNumber, sslContext, listenerService);
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    public Certificate[] getValidateSigningCertificateChain() {
        return validateSigningCertificateChain;
    }

    public PrivateKey getSigningPrivateKey() {
        return signingPrivateKey;
    }

    public PrivateKey getDecryptingPrivateKey() {
        return decryptingPrivateKey;
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
            } finally {
                lock.unlock();
            }
        }
    }

    protected HttpProcessor initProtocolProcessor(
            String as2Version,
            String originServer,
            String serverFqdn,
            AS2SignatureAlgorithm signatureAlgorithm,
            Certificate[] signingCertificateChain,
            PrivateKey signingPrivateKey,
            PrivateKey decryptingPrivateKey,
            String mdnMessageTemplate,
            Certificate[] validateSigningCertificateChain) {
        return HttpProcessorBuilder.create().add(new ResponseContent(true)).add(new ResponseServer(originServer))
                .add(new ResponseDate()).add(new ResponseConnControl()).add(new ResponseMDN(
                        as2Version, serverFqdn,
                        signatureAlgorithm, signingCertificateChain, signingPrivateKey, decryptingPrivateKey,
                        mdnMessageTemplate, validateSigningCertificateChain))
                .build();
    }

}
