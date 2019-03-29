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

import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.io.AS2BHttpServerConnection;
import org.apache.camel.component.as2.api.protocol.ResponseMDN;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.apache.http.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2ServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ServerConnection.class);

    private static final String REQUEST_LISTENER_THREAD_NAME_PREFIX = "AS2Svr-";
    private static final String REQUEST_HANDLER_THREAD_NAME_PREFIX = "AS2Hdlr-";

    class RequestListenerThread extends Thread {

        private final ServerSocket serversocket;
        private final HttpService httpService;
        private UriHttpRequestHandlerMapper reqistry;

        public RequestListenerThread(String as2Version,
                                     String originServer,
                                     String serverFqdn,
                                     int port,
                                     AS2SignatureAlgorithm signatureAlgorithm,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     PrivateKey decryptingPrivateKey)
                throws IOException {
            setName(REQUEST_LISTENER_THREAD_NAME_PREFIX + port);
            serversocket = new ServerSocket(port);

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc = initProtocolProcessor(as2Version, originServer, serverFqdn, port,
                    signatureAlgorithm, signingCertificateChain, signingPrivateKey, decryptingPrivateKey);

            reqistry = new UriHttpRequestHandlerMapper();

            // Set up the HTTP service
            httpService = new HttpService(inhttpproc, reqistry);
        }

        @Override
        public void run() {
            LOG.info("Listening on port {}", this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    final int bufsize = 8 * 1024;
                    // Set up incoming HTTP connection
                    final Socket insocket = this.serversocket.accept();
                    final AS2BHttpServerConnection inconn = new AS2BHttpServerConnection(bufsize);
                    LOG.info("Incoming connection from {}", insocket.getInetAddress());
                    inconn.bind(insocket);

                    // Start worker thread
                    final Thread t = new RequestHandlerThread(this.httpService, inconn);
                    t.setDaemon(true);
                    t.start();
                } catch (final InterruptedIOException ex) {
                    break;
                } catch (final SocketException e) {
                    // Server socket closed
                    break;
                } catch (final IOException e) {
                    LOG.error("I/O error initialising connection thread: {}", e.getMessage());
                    break;
                }
            }
        }

        void registerHandler(String requestUriPattern, HttpRequestHandler httpRequestHandler) {
            reqistry.register(requestUriPattern, httpRequestHandler);
        }

        void unregisterHandler(String requestUri) {
            reqistry.unregister(requestUri);
        }

    }

    class RequestHandlerThread extends Thread {
        private HttpService httpService;
        private HttpServerConnection serverConnection;

        public RequestHandlerThread(HttpService httpService, HttpServerConnection serverConnection) {
            if (serverConnection instanceof HttpInetConnection) {
                HttpInetConnection inetConnection = (HttpInetConnection) serverConnection;
                setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + inetConnection.getLocalPort());
            } else {
                setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + getId());
            }
            this.httpService = httpService;
            this.serverConnection = serverConnection;
        }

        @Override
        public void run() {
            LOG.info("Processing new AS2 request");
            final HttpContext context = new BasicHttpContext(null);

            try {
                while (!Thread.interrupted()) {

                    this.httpService.handleRequest(this.serverConnection, context);

                    // Send asynchronous MDN if any.
                    HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                    String recipientAddress = coreContext.getAttribute(AS2AsynchronousMDNManager.RECIPIENT_ADDRESS,
                            String.class);
                    if (recipientAddress != null) {
                        DispositionNotificationMultipartReportEntity multipartReportEntity = coreContext.getAttribute(
                                AS2AsynchronousMDNManager.ASYNCHRONOUS_MDN,
                                DispositionNotificationMultipartReportEntity.class);
                        AS2AsynchronousMDNManager asynchronousMDNManager = new AS2AsynchronousMDNManager(as2Version,
                                originServer, serverFqdn, signingCertificateChain, signingPrivateKey);
                        asynchronousMDNManager.send(multipartReportEntity, recipientAddress);
                    }

                }
            } catch (final ConnectionClosedException ex) {
                LOG.info("Client closed connection");
            } catch (final IOException ex) {
                LOG.error("I/O error: {}", ex.getMessage());
            } catch (final HttpException ex) {
                ex.printStackTrace();
                LOG.error("Unrecoverable HTTP protocol violation: {}", ex.getMessage());
            } finally {
                try {
                    this.serverConnection.shutdown();
                } catch (final IOException ignore) {
                }
            }
        }

    }

    private RequestListenerThread listenerThread;
    private String as2Version;
    private String originServer;
    private String serverFqdn;
    private Integer serverPortNumber;
    private AS2SignatureAlgorithm signingAlgorithm;
    private Certificate[] signingCertificateChain;
    private PrivateKey signingPrivateKey;
    private PrivateKey decryptingPrivateKey;

    public AS2ServerConnection(String as2Version,
                               String originServer,
                               String serverFqdn,
                               Integer serverPortNumber,
                               AS2SignatureAlgorithm signingAlgorithm,
                               Certificate[] signingCertificateChain,
                               PrivateKey signingPrivateKey,
                               PrivateKey decryptingPrivateKey)
            throws IOException {
        this.as2Version = Args.notNull(as2Version, "as2Version");
        this.originServer = Args.notNull(originServer, "userAgent");
        this.serverFqdn = Args.notNull(serverFqdn, "serverFqdn");
        this.serverPortNumber = Args.notNull(serverPortNumber, "serverPortNumber");
        this.signingAlgorithm = signingAlgorithm;
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
        this.decryptingPrivateKey = decryptingPrivateKey;

        listenerThread = new RequestListenerThread(this.as2Version, this.originServer, this.serverFqdn,
                this.serverPortNumber, this.signingAlgorithm, this.signingCertificateChain, this.signingPrivateKey, this.decryptingPrivateKey);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public PrivateKey getSigningPrivateKey() {
        return signingPrivateKey;
    }

    public PrivateKey getDecryptingPrivateKey() {
        return decryptingPrivateKey;
    }

    public void close() {
        if (listenerThread != null) {
            synchronized (listenerThread) {
                try {
                    listenerThread.serversocket.close();
                } catch (IOException e) {
                    LOG.debug(e.getMessage(), e);
                } finally {
                    listenerThread = null;
                }
            }
        }
    }

    public void listen(String requestUri, HttpRequestHandler handler) throws IOException {
        if (listenerThread != null) {
            synchronized (listenerThread) {
                listenerThread.registerHandler(requestUri, handler);
            }
        }
    }

    public void stopListening(String requestUri) {

        if (listenerThread != null) {
            listenerThread.unregisterHandler(requestUri);
        }
    }

    protected HttpProcessor initProtocolProcessor(String as2Version,
                                                  String originServer,
                                                  String serverFqdn,
                                                  int port,
                                                  AS2SignatureAlgorithm signatureAlgorithm,
                                                  Certificate[] signingCertificateChain,
                                                  PrivateKey signingPrivateKey,
                                                  PrivateKey decryptingPrivateKey) {
        return HttpProcessorBuilder.create().add(new ResponseContent(true)).add(new ResponseServer(originServer))
                .add(new ResponseDate()).add(new ResponseConnControl()).add(new ResponseMDN(as2Version, serverFqdn,
                        signatureAlgorithm, signingCertificateChain, signingPrivateKey, decryptingPrivateKey))
                .build();
    }

}
