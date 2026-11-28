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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The readiness check of the mina-sftp producer does not wait for an upload in progress on the connection: the upload
 * can last as long as the transfer takes, while the server is fine.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpProducerHealthCheckDuringUploadIT extends MinaSftpServerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HealthCheckHelper.getHealthCheckRepository(context, "producers").setEnabled(true);
        // the producer reads the body during the upload (stream caching would read it before)
        context.setStreamCaching(false);
        return context;
    }

    @Test
    void readinessDoesNotWaitForTheUpload() throws Exception {
        template.sendBodyAndHeader(uri(), "hello", Exchange.FILE_NAME, "small.txt");
        HealthCheck.Result before = producerCheck();
        assertEquals(HealthCheck.State.UP, before.getState());
        assertFalse(before.getDetails().containsKey("busy"));

        GatedInputStream body = new GatedInputStream();
        CompletableFuture<Exchange> upload = template.asyncSend(uri(), e -> {
            e.getIn().setBody(body);
            e.getIn().setHeader(Exchange.FILE_NAME, "big.bin");
        });
        ExecutorService probes = Executors.newFixedThreadPool(3);
        try {
            assertTrue(body.halfway.await(20, TimeUnit.SECONDS), "the upload started");
            List<Future<HealthCheck.Result>> calls = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                calls.add(probes.submit(this::producerCheck));
            }
            for (Future<HealthCheck.Result> call : calls) {
                // the upload holds the connection until the test lets it go on
                HealthCheck.Result during = call.get(5, TimeUnit.SECONDS);
                assertEquals(HealthCheck.State.UP, during.getState());
                assertEquals(true, during.getDetails().get("busy"));
            }
        } finally {
            body.release.countDown();
            probes.shutdownNow();
        }

        Exchange done = upload.get(20, TimeUnit.SECONDS);
        assertNull(done.getException());
        // the producer no longer uses the connection: the check sends the noop again
        HealthCheck.Result after = producerCheck();
        assertEquals(HealthCheck.State.UP, after.getState());
        assertFalse(after.getDetails().containsKey("busy"));
    }

    private HealthCheck.Result producerCheck() {
        return HealthCheckHelper.invokeReadiness(context).stream()
                .filter(r -> r.getCheck().getId().startsWith("producer:ftp-"))
                .findFirst().orElseThrow();
    }

    private String uri() {
        // the port of the server is known once it started, after the routes of the test were created
        return "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
               + "/upload?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    /**
     * An upload body that stops half way until the test lets it go on, as a big file or a slow network would.
     */
    static final class GatedInputStream extends InputStream {
        final CountDownLatch halfway = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final int size = 64 * 1024;
        private int pos;

        @Override
        public int read() throws IOException {
            if (pos == size / 2 && release.getCount() > 0) {
                halfway.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
            return pos < size ? 'a' + pos++ % 26 : -1;
        }
    }
}
