package org.apache.camel.component.as2.api;

import static org.apache.camel.component.as2.api.AS2Constants.HTTP_ORIGIN_SERVER;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2ServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ServerConnection.class);

    private static final String REQUEST_LISTENER_THREAD_NAME_PREFIX = "AS2Svr-";
    private static final String REQUEST_HANDLER_THREAD_NAME_PREFIX = "AS2Hdlr-";

    static class RequestListenerThread extends Thread {

        private final ServerSocket serversocket;
        private final HttpService httpService;

        public RequestListenerThread(int port, HttpRequestHandler httpRequestHandler) throws IOException {
            setName(REQUEST_LISTENER_THREAD_NAME_PREFIX + port);
            serversocket = new ServerSocket(port);

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc = new ImmutableHttpProcessor(
                    new HttpResponseInterceptor[] { new ResponseContent(true), new ResponseServer(HTTP_ORIGIN_SERVER),
                            new ResponseDate(), new ResponseConnControl() });

            // Set up incoming request handler
            final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
            reqistry.register("*", httpRequestHandler);

            // Set up the HTTP service
            httpService = new HttpService(inhttpproc, reqistry);
        }

        @Override
        public void run() {
            LOG.info("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    final int bufsize = 8 * 1024;
                    // Set up incoming HTTP connection
                    final Socket insocket = this.serversocket.accept();
                    final DefaultBHttpServerConnection inconn = new DefaultBHttpServerConnection(bufsize);
                    LOG.info("Incoming connection from " + insocket.getInetAddress());
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
                    LOG.error("I/O error initialising connection thread: " + e.getMessage());
                    break;
                }
            }
        }
    }

    static class RequestHandlerThread extends Thread {
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
            LOG.info("New connection thread");
            final HttpContext context = new BasicHttpContext(null);

            try {
                while (!Thread.interrupted()) {

                    this.httpService.handleRequest(this.serverConnection, context);

                }
            } catch (final ConnectionClosedException ex) {
                LOG.info("Client closed connection");
            } catch (final IOException ex) {
                LOG.error("I/O error: " + ex.getMessage());
            } catch (final HttpException ex) {
                LOG.error("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.serverConnection.shutdown();
                } catch (final IOException ignore) {
                }
            }
        }
    }
    
    private Map<Integer, RequestListenerThread> listenerThreads = new HashMap<Integer, RequestListenerThread>();

    public AS2ServerConnection() {
    }

    public void listen(HttpRequestHandler handler, int port) throws IOException {
        RequestListenerThread listenerThread = new RequestListenerThread(port, handler);
        listenerThread.setDaemon(true);
        listenerThread.start();
        synchronized (listenerThreads) {
            listenerThreads.put(port, listenerThread);
        }
    }
    
    public void stopListnering(int port) {
        RequestListenerThread thread = null;
        synchronized(listenerThreads) {
            thread = listenerThreads.remove(port);
        }
        
        if (thread != null) {
            try {
                thread.serversocket.close();
            } catch (IOException e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

}
