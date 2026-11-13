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
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kubernetes.WatchControlDispatcher;
import org.apache.camel.component.kubernetes.properties.ConfigMapPropertiesFunction;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The task that reloads the CamelContext when a config map is updated is submitted once, and watches the config maps
 * until it is stopped. The API server closes a watch with 410 Gone when the resource version of the watch is too old
 * (which can happen to a long-running watch), and then no longer has the events since that version. The task must then
 * watch again, and must not miss an update made while it was not watching.
 */
class ConfigmapsReloadTriggerTaskWatchClosedTest {

    private WatchControlDispatcher dispatcher;
    private KubernetesMockServer server;
    private NamespacedKubernetesClient client;

    @BeforeEach
    void setUp() {
        dispatcher = new WatchControlDispatcher("/api/v1/namespaces/test/configmaps");
        server = WatchControlDispatcher.createServer(dispatcher);
        client = server.createClient();
        client.configMaps().resource(new ConfigMapBuilder().withNewMetadata().withName("myconfigmap").endMetadata()
                .addToData("greeting", "one").build()).create();
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.destroy();
    }

    /**
     * The API server closes a watch with 410 Gone when the events since its resource version are gone, so an update
     * made in that time is not sent to any watch.
     */
    @Test
    void testUpdateMadeWhileTheWatchIsClosedIsNotMissed() throws Exception {
        dispatcher.holdFirstWatch();

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);

            updateConfigMap("two");
            dispatcher.closeHeldWatchWithGone();

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the config map made while the watch was closed did not trigger a reload");
        }
    }

    @Test
    void testWatchAgainAfterTheWatchIsClosedWithAnError() throws Exception {
        dispatcher.holdFirstWatch();

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);
            dispatcher.closeHeldWatchWithGone();
            dispatcher.awaitOpenWatches(2);

            updateConfigMap("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the config map after the watch was closed did not trigger a reload");
        }
    }

    @Test
    void testWatchAgainAfterAFailedAttemptToWatchAgain() throws Exception {
        dispatcher.holdFirstWatch();

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);
            // the first attempt to list or watch again fails
            dispatcher.failNextRequest(403);
            dispatcher.closeHeldWatchWithGone();
            dispatcher.awaitOpenWatches(2);

            updateConfigMap("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the config map after a failed attempt to watch again did not trigger a reload");
        }
    }

    /**
     * The config maps cannot be watched when the task starts, for example while the RBAC rules of the service account
     * are not there yet.
     */
    @Test
    void testWatchAgainAfterAFailedFirstAttemptToWatch() throws Exception {
        dispatcher.failNextRequest(403);

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new ConfigmapsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);

            updateConfigMap("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the config map after a failed first attempt to watch did not trigger a reload");
        }
    }

    /**
     * A task that is stopped while it watches ends, so it does not keep the thread that runs it, and it does not watch
     * again.
     */
    @Test
    void testStopEndsTheTaskThatWatches() throws Exception {
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
            dispatcher.awaitOpenWatches(1);

            // the task has a watch
            updateConfigMap("two");
            assertTrue(reloaded.await(20, TimeUnit.SECONDS), "the update of the config map did not trigger a reload");
        }

        // the context is stopped, and the task with it
        Thread thread = taskThread.get();
        thread.join(20000);
        assertFalse(thread.isAlive(), "the stopped task still waits for its watch to be closed");
    }

    private void updateConfigMap(String greeting) {
        client.configMaps().withName("myconfigmap")
                .edit(c -> new ConfigMapBuilder(c).addToData("greeting", greeting).build());
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
