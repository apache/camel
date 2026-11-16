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
package org.apache.camel.component.file.remote.mina.integration;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An upload that hangs, because the server stopped answering, holds the lock of {@code MinaSftpOperations} for as long
 * as it hangs, in the thread of the caller, which nothing interrupts. When Camel stops, the shutdown strategy forces
 * the shutdown after its timeout: the stop of the producer must then not wait for the upload, but disconnect, and the
 * upload fail. Outside a forced shutdown, the stop of the producer waits for the upload, as before. The same as
 * {@code SftpProducerStopDuringHungUploadIT} of camel-ftp (CAMEL-27001).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpProducerStopDuringHungUploadIT extends CamelTestSupport {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 1;
    // the shutdown is forced after its timeout: the stop should return well within this limit
    private static final int STOP_LIMIT_SECONDS = 20;

    @RegisterExtension
    HangingSftpEmbeddedService service = new HangingSftpEmbeddedService();

    // threads that Camel does not manage, so that its shutdown does not interrupt them
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void releaseServer() {
        service.release();
        executor.shutdown();
    }

    /**
     * The SFTP subsystem of the server stops answering while the SSH transport stays alive: the upload waits for the
     * status of its writes.
     */
    @Test
    void stopDoesNotWaitForUploadWhenServerStopsAnswering() throws Exception {
        service.hangTransfers();
        context.getShutdownStrategy().setTimeout(SHUTDOWN_TIMEOUT_SECONDS);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:upload").to(sftpUri());
            }
        });

        Future<Exchange> upload = executor.submit(() -> template.send("direct:upload", exchange -> {
            exchange.getIn().setBody(new byte[64 * 1024]);
            exchange.getIn().setHeader(Exchange.FILE_NAME, "big.bin");
        }));
        assertTrue(service.awaitHang(), "The upload should reach the server");

        assertStopReturns();

        // the upload failed, it did not report success
        Exchange exchange = upload.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(exchange.getException(GenericFileOperationFailedException.class),
                "The hung upload should fail, exception: " + exchange.getException());
    }

    /**
     * A route whose consumer is neither suspendable nor defers its shutdown (such as netty's): the shutdown stops it
     * right away, and with it the processors of the route, so the producer stops while the upload runs, and before the
     * shutdown is forced. Its stop disconnects once the shutdown is forced.
     */
    @Test
    void stopDoesNotWaitForUploadOfRouteWhoseConsumerStopsRightAway() throws Exception {
        service.hangTransfers();
        PlainEndpoint plain = addUploadRouteFromPlainConsumer(SHUTDOWN_TIMEOUT_SECONDS);

        Future<Exchange> upload = executor.submit(() -> plain.send(new byte[64 * 1024]));
        assertTrue(service.awaitHang(), "The upload should reach the server");

        assertStopReturns();

        Exchange exchange = upload.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(exchange.getException(GenericFileOperationFailedException.class),
                "The hung upload should fail, exception: " + exchange.getException());
    }

    /**
     * The same route, with an upload that is healthy but slow: a graceful shutdown waits for it up to its timeout, the
     * stop of the producer does not disconnect under it.
     */
    @Test
    void gracefulStopWaitsForUploadOfRouteWhoseConsumerStopsRightAway() throws Exception {
        service.hangTransfers();
        PlainEndpoint plain = addUploadRouteFromPlainConsumer(30);

        Future<Exchange> upload = executor.submit(() -> plain.send(new byte[64 * 1024]));
        assertTrue(service.awaitHang(), "The upload should reach the server");

        Future<?> stop = executor.submit(context::stop);
        // the upload completes, the exchange can still be cut off afterwards, as the shutdown stopped its route
        assertStopWaitsForUploadThatThenCompletes(stop, upload);
    }

    /**
     * A stop of the producer outside a forced shutdown, here an application that stops the ProducerTemplate another
     * thread still sends with (a producer cache that evicts the producer of a dynamic endpoint does the same): the
     * upload can be healthy but slow, so the stop waits for it rather than disconnecting under it.
     */
    @Test
    void stopOutsideForcedShutdownWaitsForUpload() throws Exception {
        service.hangTransfers();
        ProducerTemplate producerTemplate = context.createProducerTemplate();

        Future<Exchange> upload = executor.submit(() -> producerTemplate.send(sftpUri(), exchange -> {
            exchange.getIn().setBody(new byte[64 * 1024]);
            exchange.getIn().setHeader(Exchange.FILE_NAME, "big.bin");
        }));
        assertTrue(service.awaitHang(), "The upload should reach the server");

        Future<?> stop = executor.submit(producerTemplate::stop);
        Exchange exchange = assertStopWaitsForUploadThatThenCompletes(stop, upload);
        assertNull(exchange.getException(), "The exchange should complete");
    }

    private Exchange assertStopWaitsForUploadThatThenCompletes(Future<?> stop, Future<Exchange> upload) throws Exception {
        // well after the 1 s after which the stop disconnects in a forced shutdown
        try {
            await().atMost(5, TimeUnit.SECONDS).during(3, TimeUnit.SECONDS)
                    .until(() -> !stop.isDone() && !upload.isDone());
        } catch (ConditionTimeoutException e) {
            fail("The stop should wait for the upload, the stop returned: " + stop.isDone() + ", the upload ended: "
                 + upload.isDone(),
                    e);
        }

        // the server answers again: the upload completes, then the stop
        service.release();
        Exchange exchange = upload.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNull(exchange.getException(GenericFileOperationFailedException.class),
                "The upload should complete, exception: " + exchange.getException());
        stop.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(64 * 1024, Files.size(service.getFtpRootDir().resolve("upload/big.bin")));
        return exchange;
    }

    private PlainEndpoint addUploadRouteFromPlainConsumer(int shutdownTimeoutSeconds) throws Exception {
        context.getShutdownStrategy().setTimeout(shutdownTimeoutSeconds);
        PlainEndpoint plain = new PlainEndpoint(context);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(plain).to(sftpUri());
            }
        });
        return plain;
    }

    private String sftpUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/{{ftp.root.dir}}/upload?username=admin&password=admin"
               + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";
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
        }
    }

    /**
     * An endpoint whose consumer is neither suspendable nor defers its shutdown, such as netty's: the shutdown stops it
     * right away, before it waits for the in-flight exchanges, and with it the processors of its route.
     */
    static class PlainEndpoint extends DefaultEndpoint {

        private volatile Consumer consumer;

        PlainEndpoint(CamelContext context) {
            super("plain://upload", null);
            setCamelContext(context);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            consumer = new DefaultConsumer(this, processor);
            return consumer;
        }

        Exchange send(Object body) throws Exception {
            Exchange exchange = createExchange();
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader(Exchange.FILE_NAME, "big.bin");
            consumer.getProcessor().process(exchange);
            return exchange;
        }
    }

    /**
     * The embedded SFTP server. Its SFTP subsystem can stop answering at the first write of file data, until
     * {@link #release()}.
     */
    static class HangingSftpEmbeddedService extends SftpEmbeddedService {

        private final CountDownLatch hanging = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean hangTransfers;

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            SftpSubsystemFactory factory = (SftpSubsystemFactory) sshd.getSubsystemFactories().get(0);
            factory.addSftpEventListener(new SftpEventListener() {
                @Override
                public void writing(
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
}
