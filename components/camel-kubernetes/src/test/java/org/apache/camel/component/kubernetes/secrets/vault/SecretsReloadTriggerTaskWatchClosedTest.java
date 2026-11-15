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
package org.apache.camel.component.kubernetes.secrets.vault;

import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kubernetes.WatchControlDispatcher;
import org.apache.camel.component.kubernetes.properties.SecretPropertiesFunction;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The task that reloads the CamelContext when a secret is updated is submitted once, and watches the secrets until it
 * is stopped. The API server closes a watch with 410 Gone when the resource version of the watch is too old (which can
 * happen to a long-running watch), and then no longer has the events since that version. The task must then watch
 * again, and must not miss an update made while it was not watching.
 */
class SecretsReloadTriggerTaskWatchClosedTest {

    private WatchControlDispatcher dispatcher;
    private KubernetesMockServer server;
    private NamespacedKubernetesClient client;

    @BeforeEach
    void setUp() {
        dispatcher = new WatchControlDispatcher("/api/v1/namespaces/test/secrets");
        server = WatchControlDispatcher.createServer(dispatcher);
        client = server.createClient();
        client.secrets().resource(new SecretBuilder().withNewMetadata().withName("mysecret").endMetadata()
                .addToData("password", encode("one")).build()).create();
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
            schedule(context, new SecretsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);

            updateSecret("two");
            dispatcher.closeHeldWatchWithGone();

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the secret made while the watch was closed did not trigger a reload");
        }
    }

    @Test
    void testWatchAgainAfterTheWatchIsClosedWithAnError() throws Exception {
        dispatcher.holdFirstWatch();

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new SecretsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);
            dispatcher.closeHeldWatchWithGone();
            dispatcher.awaitOpenWatches(2);

            updateSecret("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the secret after the watch was closed did not trigger a reload");
        }
    }

    @Test
    void testWatchAgainAfterAFailedAttemptToWatchAgain() throws Exception {
        dispatcher.holdFirstWatch();

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new SecretsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);
            // the first attempt to list or watch again fails
            dispatcher.failNextRequest(403);
            dispatcher.closeHeldWatchWithGone();
            dispatcher.awaitOpenWatches(2);

            updateSecret("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the secret after a failed attempt to watch again did not trigger a reload");
        }
    }

    /**
     * The secrets cannot be watched when the task starts, for example while the RBAC rules of the service account are
     * not there yet.
     */
    @Test
    void testWatchAgainAfterAFailedFirstAttemptToWatch() throws Exception {
        dispatcher.failNextRequest(403);

        CountDownLatch reloaded = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            schedule(context, new SecretsReloadTriggerTask(), reloaded);
            dispatcher.awaitOpenWatches(1);

            updateSecret("two");

            assertTrue(reloaded.await(20, TimeUnit.SECONDS),
                    "the update of the secret after a failed first attempt to watch did not trigger a reload");
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
            schedule(context, new SecretsReloadTriggerTask() {
                @Override
                public void run() {
                    taskThread.set(Thread.currentThread());
                    super.run();
                }
            }, reloaded);
            dispatcher.awaitOpenWatches(1);

            // the task has a watch
            updateSecret("two");
            assertTrue(reloaded.await(20, TimeUnit.SECONDS), "the update of the secret did not trigger a reload");
        }

        // the context is stopped, and the task with it
        Thread thread = taskThread.get();
        thread.join(20000);
        assertFalse(thread.isAlive(), "the stopped task still waits for its watch to be closed");
    }

    private void updateSecret(String password) {
        client.secrets().withName("mysecret")
                .edit(s -> new SecretBuilder(s).addToData("password", encode(password)).build());
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private void schedule(CamelContext context, SecretsReloadTriggerTask task, CountDownLatch reloaded) throws Exception {
        SecretPropertiesFunction function = new SecretPropertiesFunction();
        function.setCamelContext(context);
        function.setClient(client);
        context.getPropertiesComponent().addPropertiesFunction(function);
        context.getVaultConfiguration().kubernetes().setSecrets("mysecret");
        context.addService(new DefaultContextReloadStrategy() {
            @Override
            public void onReload(Object source) {
                reloaded.countDown();
            }
        });
        // scheduled the way camel-main does with camel.vault.kubernetes.refreshEnabled=true
        PluginHelper.getPeriodTaskScheduler(context).scheduledTask(task);
        context.start();
    }
}
