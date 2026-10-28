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
package org.apache.camel.component.kubernetes.config_maps.vault;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kubernetes.properties.ConfigMapPropertiesFunction;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The task that reloads the CamelContext when a config map is updated is submitted once, and the Kubernetes client
 * closes its watch for good, with an exception, when the API server answers 410 Gone (the resource version of the watch
 * is too old, which can happen to a long-running watch). The task must then watch again.
 */
@EnableKubernetesMockClient
class ConfigmapsReloadTriggerTaskWatchClosedTest {

    private static final String WATCH_PATH = "/api/v1/namespaces/test/configmaps?allowWatchBookmarks=true&watch=true";

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @Test
    void testWatchAgainAfterTheWatchIsClosedWithAnError() throws Exception {
        expectWatchClosedWithGone();
        expectWatchWithUpdate();

        assertReloadIsTriggered();
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void testWatchAgainAfterAFailedAttemptToWatch() throws Exception {
        expectWatchClosedWithGone();
        // the first attempt to watch again fails
        server.expect().withPath(WATCH_PATH)
                .andReturn(403, new StatusBuilder().withCode(403).withReason("Forbidden").withMessage("forbidden").build())
                .once();
        expectWatchWithUpdate();

        // watched again after 1 second, failed, and watched again after 2 more seconds
        assertReloadIsTriggered();
        assertEquals(3, server.getRequestCount());
    }

    /**
     * A task that is stopped while it watches ends, so it does not keep the thread that runs it, and it does not watch
     * again.
     */
    @Test
    void testStopEndsTheTaskThatWatches() throws Exception {
        expectWatchWithUpdate();

        CountDownLatch reloaded = new CountDownLatch(1);
        AtomicReference<Thread> taskThread = new AtomicReference<>();
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask() {
                @Override
                public void run() {
                    taskThread.set(Thread.currentThread());
                    super.run();
                }
            }, reloaded);

            // the task has a watch
            assertTrue(reloaded.await(20, TimeUnit.SECONDS), "the update of the config map did not trigger a reload");
        }

        // the context is stopped, and the task with it
        Thread thread = taskThread.get();
        thread.join(20000);
        assertFalse(thread.isAlive(), "the stopped task still waits for its watch to be closed");
        assertEquals(1, server.getRequestCount());
    }

    private void expectWatchClosedWithGone() {
        server.expect().withPath(WATCH_PATH)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10)
                .andEmit(new WatchEvent(
                        new StatusBuilder().withCode(410).withReason("Expired").withMessage("too old resource version")
                                .build(),
                        "ERROR"))
                .done()
                .once();
    }

    private void expectWatchWithUpdate() {
        server.expect().withPath(WATCH_PATH)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10)
                .andEmit(new WatchEvent(
                        new ConfigMapBuilder().withNewMetadata().withName("myconfigmap").withNamespace("test")
                                .withResourceVersion("2").endMetadata().build(),
                        "MODIFIED"))
                .done()
                .once();
    }

    private void assertReloadIsTriggered() throws Exception {
        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask(), reloaded);

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the config map after the watch was closed did not trigger a reload");
        }
    }

    private void schedule(CamelContext context, ConfigmapsReloadTriggerTask task, CountDownLatch reloaded) throws Exception {
        ConfigMapPropertiesFunction function = new ConfigMapPropertiesFunction();
        function.setCamelContext(context);
        function.setClient(client);
        context.getPropertiesComponent().addPropertiesFunction(function);
        context.getVaultConfiguration().kubernetesConfigmaps().setConfigmaps("myconfigmap");
        context.addService(new DefaultContextReloadStrategy() {
            @Override
            public void onReload(Object source) {
                reloaded.countDown();
            }
        });
        // scheduled the way camel-main does with camel.vault.kubernetescm.refreshEnabled=true
        PluginHelper.getPeriodTaskScheduler(context).scheduledTask(task);
        context.start();
    }
}
