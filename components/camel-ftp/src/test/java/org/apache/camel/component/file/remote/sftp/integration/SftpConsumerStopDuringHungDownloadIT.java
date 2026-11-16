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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.sftp.integration.SftpProducerStopDuringHungUploadIT.HangingSftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An sftp download that hangs, because the server stopped answering, holds the lock of {@code SftpOperations} in the
 * thread of the poll. When Camel stops, the shutdown strategy forces the shutdown after its timeout, and the stop of
 * the consumer interrupts the poll: the download fails, but the poll then changes back to the directory it started from
 * (stepwise), which the server does not answer either, with the lock. The stop of the consumer must then not wait for
 * the lock, but disconnect, as the stop of the producer does (CAMEL-27001, CAMEL-27018).
 * <p>
 * The stop of the scheduler interrupts the poll once when the consumer runs on a shared scheduled thread pool (it
 * cancels the poll), twice when the consumer has its own pool (it shuts the pool down as well): the second interrupt
 * often, but not always, ends the change of directory too.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpConsumerStopDuringHungDownloadIT extends CamelTestSupport {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 1;
    // the shutdown is forced after its timeout: the stop should return well within this limit
    private static final int STOP_LIMIT_SECONDS = 20;

    @RegisterExtension
    HangingSftpEmbeddedService service = new HangingSftpEmbeddedService();

    // threads that Camel does not manage, so that its shutdown does not interrupt them
    private final ExecutorService executor = Executors.newCachedThreadPool();
    // a scheduled thread pool that the consumer shares, so its stop does not shut it down
    private final ScheduledExecutorService sharedPool = Executors.newScheduledThreadPool(1);

    @AfterEach
    void releaseServer() {
        service.release();
        executor.shutdown();
        sharedPool.shutdownNow();
    }

    @Test
    void stopDoesNotWaitForDownloadWhenServerStopsAnswering() throws Exception {
        assertStopDoesNotWaitForDownload("");
    }

    @Test
    void stopDoesNotWaitForDownloadOfConsumerOnSharedScheduledPool() throws Exception {
        context.getRegistry().bind("sharedPool", sharedPool);
        assertStopDoesNotWaitForDownload("&scheduledExecutorService=#sharedPool");
    }

    private void assertStopDoesNotWaitForDownload(String options) throws Exception {
        Path download = service.getFtpRootDir().resolve("download");
        Files.createDirectories(download);
        Files.write(download.resolve("big.bin"), new byte[64 * 1024]);
        service.hangTransfers();

        context.getShutdownStrategy().setTimeout(SHUTDOWN_TIMEOUT_SECONDS);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://admin@localhost:" + service.getPort() + "/{{ftp.root.dir}}/download?password=admin"
                     + "&knownHostsFile=" + service.getKnownHostsFile() + "&delay=100" + options)
                        .to("mock:result");
            }
        });
        assertTrue(service.awaitHang(), "The download should reach the server");

        Future<?> stop = executor.submit(context::stop);
        try {
            stop.get(STOP_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("CamelContext.stop() should return after the shutdown timeout of " + SHUTDOWN_TIMEOUT_SECONDS
                 + " s, it still waits for the download after " + STOP_LIMIT_SECONDS + " s");
        } finally {
            // so that a stop that waits for the download can complete
            service.release();
        }
        // the download failed: the file was not routed
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
    }
}
