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
package org.apache.camel.component.platform.http.vertx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class VertxPlatformHttpClientDisconnectTest {
    private static final long RESPONSE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_PRODUCED_AFTER_ABORT = 16L * 1024 * 1024;
    private static final int BYTES_READ_BEFORE_ABORT = 64 * 1024;
    private static final int BYTES_BEFORE_STREAM_FAILURE = 64 * 1024;

    @Test
    void serverStopsReadingBodyWhenClientAborts() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        final CountingInputStream body = new CountingInputStream(RESPONSE_SIZE);
        final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();
        context.getRegistry().bind("recordingExceptionHandler", exceptionHandler);
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/download?exceptionHandler=#recordingExceptionHandler")
                            .process(exchange -> {
                                // stream the body as-is, so that writing the response is what consumes it
                                exchange.getExchangeExtension().setStreamCacheDisabled(true);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
                                exchange.getMessage().setBody(body);
                            });
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);
            VertxPlatformHttpServer server = context.hasService(VertxPlatformHttpServer.class);

            readSomeThenAbort(server.getPort());

            assertTrue(body.awaitClose(20, TimeUnit.SECONDS),
                    "The response body stream was never closed after the client aborted the request");

            long produced = body.produced();
            assertTrue(produced < MAX_PRODUCED_AFTER_ABORT,
                    () -> "The server kept reading the response body after the client aborted: produced "
                          + produced + " bytes of " + RESPONSE_SIZE);
            assertTrue(exceptionHandler.handled.isEmpty(),
                    () -> "The client aborting the request was reported as a failure: " + exceptionHandler.handled);
        } finally {
            context.stop();
        }
    }

    @Test
    void failureAfterResponseStartedIsNotReportedAsAnUnhandledRouterFailure() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        final List<String> routerErrors = new CopyOnWriteArrayList<>();
        final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();
        context.getRegistry().bind("recordingExceptionHandler", exceptionHandler);

        AbstractAppender appender = new AbstractAppender("CaptureRouterErrors", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.ERROR) {
                    routerErrors.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger routerLogger = (Logger) LogManager.getLogger(RoutingContext.class);
        routerLogger.addAppender(appender);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/failing-download?exceptionHandler=#recordingExceptionHandler")
                            .process(exchange -> {
                                exchange.getExchangeExtension().setStreamCacheDisabled(true);
                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
                                exchange.getMessage().setBody(new FailingInputStream(BYTES_BEFORE_STREAM_FAILURE));
                            });
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);
            VertxPlatformHttpServer server = context.hasService(VertxPlatformHttpServer.class);

            readUntilTheResponseBreaks(server.getPort());

            assertTrue(routerErrors.isEmpty(),
                    () -> "A response that failed after it had started was logged as an unhandled router failure: "
                          + routerErrors);
            assertTrue(exceptionHandler.handled.stream().anyMatch(IOException.class::isInstance),
                    () -> "The body stream failure was not reported to the exception handler: "
                          + exceptionHandler.handled);
        } finally {
            routerLogger.removeAppender(appender);
            appender.stop();
            context.stop();
        }
    }

    private static void readUntilTheResponseBreaks(int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            socket.setSoTimeout(10000);

            OutputStream out = socket.getOutputStream();
            out.write("GET /failing-download HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            try {
                while (in.read(buffer) != -1) {
                    // drain until the server resets or ends the connection
                }
            } catch (SocketTimeoutException e) {
                fail("The server left the client waiting on a truncated response instead of terminating it");
            } catch (IOException expected) {
                // the server resets the stream once the body fails
            }
        }
    }

    private static void readSomeThenAbort(int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            socket.setSoTimeout(20000);

            OutputStream out = socket.getOutputStream();
            out.write("GET /download HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            while (total < BYTES_READ_BEFORE_ABORT) {
                int read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                total += read;
            }

            socket.setSoLinger(true, 0);
        }
    }

    private static final class RecordingExceptionHandler implements ExceptionHandler {

        private final List<Throwable> handled = new CopyOnWriteArrayList<>();

        @Override
        public void handleException(Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handled.add(exception);
        }
    }

    private static final class CountingInputStream extends InputStream {

        private final AtomicLong produced = new AtomicLong();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final byte[] block = new byte[8192];
        private final long size;

        private CountingInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            byte[] single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            long alreadyProduced = produced.get();
            if (alreadyProduced >= size) {
                return -1;
            }
            int count = (int) Math.min(Math.min(len, block.length), size - alreadyProduced);
            System.arraycopy(block, 0, b, off, count);
            produced.addAndGet(count);
            return count;
        }

        @Override
        public void close() {
            closed.countDown();
        }

        private long produced() {
            return produced.get();
        }

        private boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
            return closed.await(timeout, unit);
        }
    }

    private static final class FailingInputStream extends InputStream {

        private final byte[] block = new byte[8192];
        private final int bytesBeforeFailure;
        private int produced;

        private FailingInputStream(int bytesBeforeFailure) {
            this.bytesBeforeFailure = bytesBeforeFailure;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (produced >= bytesBeforeFailure) {
                throw new IOException("Source failed while the response was being written");
            }
            int count = Math.min(Math.min(len, block.length), bytesBeforeFailure - produced);
            System.arraycopy(block, 0, b, off, count);
            produced += count;
            return count;
        }
    }
}
