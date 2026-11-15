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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An sftp upload that hangs, because the server stopped answering, holds the lock of {@code SftpOperations} for as long
 * as it hangs, in the thread of the caller, which nothing interrupts. When Camel stops, the shutdown strategy forces
 * the shutdown after its timeout: the stop of the producer must then not wait for the upload, but disconnect, and the
 * upload fail (CAMEL-27001).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpProducerStopDuringHungUploadIT extends CamelTestSupport {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 1;
    // the shutdown is forced after its timeout: the stop should return well within this limit
    private static final int STOP_LIMIT_SECONDS = 20;

    @RegisterExtension
    HangingSftpEmbeddedService service = new HangingSftpEmbeddedService();

    // threads that Camel does not manage, so that its shutdown does not interrupt them
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private FreezingProxy proxy;

    @AfterEach
    void releaseServer() throws IOException {
        service.release();
        if (proxy != null) {
            proxy.close();
        }
        executor.shutdown();
    }

    /**
     * The SFTP subsystem of the server stops answering while the SSH transport stays alive: the upload waits for the
     * status of its writes.
     */
    @Test
    void stopDoesNotWaitForUploadWhenServerStopsAnswering() throws Exception {
        service.hangTransfers();
        addUploadRoute(service.getPort(), "");

        Future<Exchange> upload = executor.submit(() -> upload(new byte[64 * 1024]));
        assertTrue(service.awaitHang(), "The upload should reach the server");

        assertStopReturns();

        // the upload failed, it did not report success
        Exchange exchange = upload.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(exchange.getException(GenericFileOperationFailedException.class),
                "The hung upload should fail, exception: " + exchange.getException());
    }

    /**
     * The server stops reading from the connection, as when it becomes half-open: the upload blocks writing to the
     * socket, with the write lock of the JSch session, which the close of the channel needs as well.
     */
    @Test
    void stopDoesNotWaitForUploadBlockedWritingToHalfOpenConnection() throws Exception {
        proxy = new FreezingProxy(service.getPort(), executor);
        // JSch waits for the status of its writes once 16 of them are outstanding (by default): allow enough of them for
        // the upload to fill the TCP buffers, whatever their size, and block writing to the socket
        addUploadRoute(proxy.getPort(), "&bulkRequests=4096");

        AtomicReference<Thread> uploader = new AtomicReference<>();
        Future<Exchange> upload = executor.submit(() -> {
            uploader.set(Thread.currentThread());
            return upload(zeros(256 * 1024 * 1024));
        });
        // the proxy stops reading after the first MB of the upload, the TCP buffers fill, and the write blocks
        try {
            await().atMost(20, TimeUnit.SECONDS).during(500, TimeUnit.MILLISECONDS)
                    .until(() -> proxy.isFrozen() && isWritingToSocket(uploader.get()));
        } catch (ConditionTimeoutException e) {
            fail("The upload should block writing to the socket, frozen: " + proxy.isFrozen() + ", the upload is at: "
                 + Arrays.toString(uploader.get().getStackTrace()),
                    e);
        }

        assertStopReturns();

        // the write only fails once the connection breaks
        proxy.close();
        Exchange exchange = upload.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(exchange.getException(GenericFileOperationFailedException.class),
                "The blocked upload should fail, exception: " + exchange.getException());
    }

    private void addUploadRoute(int port, String options) throws Exception {
        context.getShutdownStrategy().setTimeout(SHUTDOWN_TIMEOUT_SECONDS);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:upload")
                        .to("sftp://admin@localhost:" + port + "/{{ftp.root.dir}}/upload?password=admin&knownHostsFile="
                            + service.getKnownHostsFile() + options);
            }
        });
    }

    private Exchange upload(Object body) {
        return template.send("direct:upload", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader(Exchange.FILE_NAME, "big.bin");
        });
    }

    private void assertStopReturns() throws Exception {
        Future<?> stop = executor.submit(context::stop);
        try {
            stop.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("CamelContext.stop() should return after the shutdown timeout of " + SHUTDOWN_TIMEOUT_SECONDS
                 + " s, it still waits for the upload after " + STOP_LIMIT_SECONDS + " s");
        } finally {
            // so that a stop that waits for the upload can complete
            service.release();
            if (proxy != null) {
                proxy.close();
            }
        }
    }

    private static boolean isWritingToSocket(Thread thread) {
        return thread != null && Arrays.stream(thread.getStackTrace())
                .anyMatch(e -> e.getClassName().equals("com.jcraft.jsch.IO") && e.getMethodName().equals("put"));
    }

    private static InputStream zeros(long size) {
        return new InputStream() {
            private long remaining = size;

            @Override
            public int read() {
                if (remaining <= 0) {
                    return -1;
                }
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, remaining);
                Arrays.fill(b, off, off + n, (byte) 0);
                remaining -= n;
                return n;
            }
        };
    }

    /**
     * The embedded SFTP server. Its SFTP subsystem can stop answering at the first read or write of file data, until
     * {@link #release()}. Its receive window is large, so that a client that sends faster than the server reads blocks
     * on the TCP connection rather than on the SSH window.
     */
    static class HangingSftpEmbeddedService extends SftpEmbeddedService {

        private final CountDownLatch hanging = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean hangTransfers;

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            CoreModuleProperties.WINDOW_SIZE.set(sshd, 1024L * 1024 * 1024);
            SftpSubsystemFactory factory = (SftpSubsystemFactory) sshd.getSubsystemFactories().get(0);
            factory.addSftpEventListener(new SftpEventListener() {
                @Override
                public void writing(
                        ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data,
                        int dataOffset, int dataLen) {
                    hang();
                }

                @Override
                public void reading(
                        ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data,
                        int dataOffset, int dataLen) {
                    hang();
                }
            });
        }

        void hangTransfers() {
            hangTransfers = true;
        }

        private void hang() {
            if (!hangTransfers) {
                return;
            }
            hanging.countDown();
            try {
                released.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        boolean awaitHang() throws InterruptedException {
            return hanging.await(20, TimeUnit.SECONDS);
        }

        void release() {
            released.countDown();
        }
    }

    /**
     * A TCP proxy to the server, which stops reading from the client after the first MB it forwarded, until it is
     * closed: the client fills the TCP buffers, and its writes block, as with a half-open connection.
     */
    static class FreezingProxy implements Closeable {

        private static final long FREEZE_AFTER = 1024 * 1024;

        private final ServerSocket serverSocket;
        private final int serverPort;
        private final ExecutorService executor;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final AtomicLong fromClient = new AtomicLong();
        private final CountDownLatch closed = new CountDownLatch(1);

        FreezingProxy(int serverPort, ExecutorService executor) throws IOException {
            this.serverPort = serverPort;
            this.executor = executor;
            serverSocket = new ServerSocket();
            // a small buffer, for the client to fill quickly once the proxy stops reading
            serverSocket.setReceiveBufferSize(16 * 1024);
            serverSocket.bind(new InetSocketAddress("localhost", 0));
            executor.submit(this::accept);
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        boolean isFrozen() {
            return fromClient.get() >= FREEZE_AFTER;
        }

        private Void accept() throws IOException {
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                Socket server = new Socket("localhost", serverPort);
                sockets.add(client);
                sockets.add(server);
                executor.submit(() -> forward(client, server, true));
                executor.submit(() -> forward(server, client, false));
            }
            return null;
        }

        private Void forward(Socket from, Socket to, boolean fromTheClient) throws IOException, InterruptedException {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buffer = new byte[8192];
            while (true) {
                if (fromTheClient && isFrozen()) {
                    closed.await();
                    return null;
                }
                int n = in.read(buffer);
                if (n < 0) {
                    to.close();
                    return null;
                }
                out.write(buffer, 0, n);
                if (fromTheClient) {
                    fromClient.addAndGet(n);
                }
            }
        }

        @Override
        public void close() throws IOException {
            closed.countDown();
            serverSocket.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }
}
