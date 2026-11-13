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
package org.apache.camel.component.kubernetes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The updates that {@link KubernetesInformerLoop} calls back for. The informer calls the handler in the order of the
 * events, so the first update it calls back for tells whether an earlier event was taken as an update.
 */
class KubernetesInformerLoopTest {

    private WatchControlDispatcher dispatcher;
    private KubernetesMockServer server;
    private NamespacedKubernetesClient client;
    private final ServiceSupport service = new ServiceSupport() {
    };
    private final BlockingQueue<String> updates = new LinkedBlockingQueue<>();
    private KubernetesInformerLoop<ConfigMap> loop;
    private Thread thread;

    @BeforeEach
    void setUp() {
        dispatcher = new WatchControlDispatcher("/api/v1/namespaces/test/configmaps");
        server = WatchControlDispatcher.createServer(dispatcher);
        client = server.createClient();
        create("my-a");
        create("my-b");
        create("other");
    }

    @AfterEach
    void tearDown() throws Exception {
        service.stop();
        if (thread != null) {
            loop.stop();
            thread.join(20000);
        }
        client.close();
        server.destroy();
    }

    @Test
    void testOnlyAnUpdateOfAMatchingResourceIsAnUpdate() throws Exception {
        start();
        dispatcher.awaitOpenWatches(1);

        update("other");
        create("my-c");
        client.configMaps().withName("my-a").delete();
        update("my-b");

        assertEquals("my-b", updates.poll(20, TimeUnit.SECONDS));
    }

    @Test
    void testResourcesListedAgainAfterGoneAreUpdatesOnlyWhenChanged() throws Exception {
        dispatcher.holdFirstWatch();
        start();
        dispatcher.awaitOpenWatches(1);

        // not sent to the held watch
        update("my-b");
        dispatcher.closeHeldWatchWithGone();
        dispatcher.awaitOpenWatches(2);
        update("my-a");

        // my-a was listed again unchanged before it was updated
        assertEquals("my-b", updates.poll(20, TimeUnit.SECONDS));
        assertEquals("my-a", updates.poll(20, TimeUnit.SECONDS));
    }

    /**
     * The Kubernetes client stops the informer for good after a watch error other than 410 Gone: a new informer is
     * started, and it reports an update made in between.
     */
    @Test
    void testUpdateMadeWhileTheInformerIsStartedAgainIsReported() throws Exception {
        dispatcher.holdFirstWatch();
        start();
        dispatcher.awaitOpenWatches(1);

        // not sent to the held watch
        update("my-b");
        dispatcher.sendInvalidObjectOnHeldWatch();
        dispatcher.awaitOpenWatches(2);
        update("my-a");

        assertEquals("my-b", updates.poll(20, TimeUnit.SECONDS));
        assertEquals("my-a", updates.poll(20, TimeUnit.SECONDS));
    }

    @Test
    void testStopEndsTheLoop() throws Exception {
        start();
        dispatcher.awaitOpenWatches(1);
        update("my-a");
        assertEquals("my-a", updates.poll(20, TimeUnit.SECONDS));

        service.stop();
        loop.stop();

        thread.join(20000);
        assertFalse(thread.isAlive(), "the loop still runs after the service was stopped");
    }

    /**
     * The service starts a new loop when it is started again, so a loop that is stopped ends even when the service runs
     * again.
     */
    @Test
    void testStopEndsTheLoopWhileTheServiceRuns() throws Exception {
        start();
        dispatcher.awaitOpenWatches(1);
        update("my-a");
        assertEquals("my-a", updates.poll(20, TimeUnit.SECONDS));

        loop.stop();

        thread.join(10000);
        assertFalse(thread.isAlive(), "the stopped loop still runs");
    }

    /**
     * A stop while the informer lists the resources, before it watches them, ends the loop without watching.
     */
    @Test
    void testStopWhileTheInformerStartsEndsTheLoop() throws Exception {
        dispatcher.delayNextList(3);
        start();
        dispatcher.awaitListRequest();

        service.stop();
        loop.stop();

        thread.join(20000);
        assertFalse(thread.isAlive(), "the loop still runs after the service was stopped");
        assertEquals(0, dispatcher.watchRequests(), "the stopped loop watched the resources");
    }

    private void start() {
        service.start();
        loop = new KubernetesInformerLoop<>(
                service, "ConfigMaps", client.configMaps(), name -> name.startsWith("my-"),
                c -> updates.add(c.getMetadata().getName()));
        thread = new Thread(loop::run, "informer-loop");
        thread.start();
    }

    private void create(String name) {
        client.configMaps().resource(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata()
                .addToData("greeting", "one").build()).create();
    }

    private void update(String name) {
        client.configMaps().withName(name).edit(c -> new ConfigMapBuilder(c).addToData("greeting", "two").build());
    }
}
