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
package org.apache.camel.component.aws.secretsmanager.vault;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.vault.AwsVaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call to CloudTrail or SQS that hangs ends after the API call timeout, so the refresh task does not hold the thread
 * that runs every periodic task of the CamelContext. Runs against a local HTTP server that never answers.
 */
class CloudTrailReloadTriggerTaskCallTimeoutTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    // released at the end of the test: until then the server does not answer
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch requestArrived = new CountDownLatch(1);
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final ExecutorService serverThreads = Executors.newCachedThreadPool();
    private final ExecutorService caller = Executors.newSingleThreadExecutor();
    private HttpServer server;
    private DefaultCamelContext context;
    private CloudTrailReloadTriggerTask task;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::hang);
        server.setExecutor(serverThreads);
        server.start();

        context = new DefaultCamelContext();
        AwsVaultConfiguration aws = new AwsVaultConfiguration();
        aws.setAccessKey("ak");
        aws.setSecretKey("sk");
        aws.setRegion("us-east-1");
        aws.setSecrets("tracked-secret");
        aws.setOverrideEndpoint(true);
        aws.setUriEndpointOverride("http://localhost:" + server.getAddress().getPort());
        context.getVaultConfiguration().setAwsVaultConfiguration(aws);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        if (task != null) {
            task.stop();
            task.shutdown();
        }
        context.stop();
        caller.shutdownNow();
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private void hang(HttpExchange http) throws IOException {
        try (InputStream in = http.getRequestBody()) {
            requestBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        requestArrived.countDown();
        try {
            release.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        http.sendResponseHeaders(500, -1);
        http.close();
    }

    private void startTask() {
        task = new CloudTrailReloadTriggerTask();
        task.setCamelContext(context);
        task.setApiCallTimeout(TIMEOUT);
        task.start();
    }

    private void assertRunEndsWithinTheTimeout() throws Exception {
        Future<?> run = caller.submit(task::run);
        assertTrue(requestArrived.await(10, TimeUnit.SECONDS), "the task calls AWS");
        // the call timeout, plus some slack for a slow machine; the store itself would hold the call for 60 seconds
        run.get(5, TimeUnit.SECONDS);
    }

    @Test
    void lookupThatHangsEndsAfterTheCallTimeout() throws Exception {
        startTask();
        assertRunEndsWithinTheTimeout();
    }

    @Test
    void receiveThatHangsEndsAfterTheCallTimeout() throws Exception {
        context.getVaultConfiguration().aws().setUseSqsNotification(true);
        context.getVaultConfiguration().aws().setSqsQueueUrl("http://localhost:" + server.getAddress().getPort() + "/queue");
        startTask();
        assertRunEndsWithinTheTimeout();
        // JSON or query protocol
        assertTrue(requestBodies.get(0).matches("(?s).*WaitTimeSeconds\\W+0\\b.*"),
                "the receive must not wait for messages, but was: " + requestBodies.get(0));
    }

    @Test
    void lookupThatHangsDoesNotStallTheOtherPeriodicTasks() throws Exception {
        context.start();
        startTask();
        // stands for any other periodic task of the CamelContext, run by the same thread as the refresh task
        AtomicReference<CountDownLatch> probeRuns = new AtomicReference<>(new CountDownLatch(0));
        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        scheduler.schedulePeriodTask(() -> probeRuns.get().countDown(), 1000);
        scheduler.schedulePeriodTask(task, 1000);

        assertTrue(requestArrived.await(20, TimeUnit.SECONDS), "the task calls CloudTrail");
        CountDownLatch afterHang = new CountDownLatch(2);
        probeRuns.set(afterHang);
        assertTrue(afterHang.await(10, TimeUnit.SECONDS),
                "the other periodic task must keep running while CloudTrail does not answer");
    }
}
