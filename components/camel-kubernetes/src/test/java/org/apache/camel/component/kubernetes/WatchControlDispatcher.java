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

import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.http.ByteString;
import io.fabric8.mockwebserver.http.MockResponse;
import io.fabric8.mockwebserver.http.RecordedRequest;
import io.fabric8.mockwebserver.http.Response;
import io.fabric8.mockwebserver.http.WebSocket;
import io.fabric8.mockwebserver.http.WebSocketListener;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Kubernetes mock server dispatcher in CRUD mode that lets a test fail the next requests for a kind of resources,
 * hold the first watch of these resources (it then receives no event) and close it with 410 Gone, and wait until a
 * number of watches are open. A held watch that is closed with 410 Gone is how the API server tells that the events
 * since the resource version of the watch are gone: an update made in that time is not sent to any watch.
 */
public class WatchControlDispatcher extends KubernetesCrudDispatcher {

    private static final KubernetesSerialization SERIALIZATION = new KubernetesSerialization();

    private final String resourcesPath;
    private final Queue<Integer> failures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger watches = new AtomicInteger();
    private final Semaphore openWatches = new Semaphore(0);
    private final Semaphore closedWatches = new Semaphore(0);
    private final Semaphore listRequests = new Semaphore(0);
    private final CompletableFuture<WebSocket> heldWatch = new CompletableFuture<>();
    private volatile boolean holdFirstWatch;
    private volatile long nextListDelaySeconds;
    private volatile WebSocket lastOpenWatch;

    /**
     * @param resourcesPath the path of the resources, for example /api/v1/namespaces/test/secrets
     */
    public WatchControlDispatcher(String resourcesPath) {
        this.resourcesPath = resourcesPath;
    }

    public static KubernetesMockServer createServer(WatchControlDispatcher dispatcher) {
        KubernetesMockServer server
                = new KubernetesMockServer(new Context(), new MockWebServer(), new HashMap<>(), dispatcher, false);
        server.init();
        return server;
    }

    /**
     * The first watch of the resources is held: it receives no event until it is closed with
     * {@link #closeHeldWatchWithGone()}.
     */
    public void holdFirstWatch() {
        holdFirstWatch = true;
    }

    /**
     * The next request (list or watch) of the resources is answered with this status code.
     */
    public void failNextRequest(int code) {
        failures.add(code);
    }

    /**
     * The answer to the next list request of the resources is delayed by the given number of seconds.
     */
    public void delayNextList(long seconds) {
        nextListDelaySeconds = seconds;
    }

    /**
     * Waits until a list request of the resources is received.
     */
    public void awaitListRequest() throws InterruptedException {
        assertTrue(listRequests.tryAcquire(20, TimeUnit.SECONDS), "the resources were not listed");
    }

    /**
     * The number of watch requests of the resources so far.
     */
    public int watchRequests() {
        return watches.get();
    }

    /**
     * Waits until the given number of watches of the resources have been opened in total.
     */
    public void awaitOpenWatches(int count) throws InterruptedException {
        // the permits are released once per open watch
        assertTrue(openWatches.tryAcquire(count, 20, TimeUnit.SECONDS), "watch " + count + " was not opened");
        openWatches.release(count);
    }

    /**
     * Waits until the given number of watches of the CRUD dispatcher (not the held one) have been closed in total.
     */
    public void awaitClosedWatches(int count) throws InterruptedException {
        assertTrue(closedWatches.tryAcquire(count, 20, TimeUnit.SECONDS), "watch " + count + " was not closed");
        closedWatches.release(count);
    }

    /**
     * Closes the held first watch with 410 Gone, as the API server does when the resource version of a watch is too
     * old.
     */
    public void closeHeldWatchWithGone() throws Exception {
        WebSocket webSocket = heldWatch.get(20, TimeUnit.SECONDS);
        Status gone = new StatusBuilder().withCode(410).withReason("Expired").withMessage("too old resource version")
                .build();
        webSocket.send(SERIALIZATION.asJson(new WatchEvent(gone, "ERROR")));
    }

    /**
     * Sends an event that is not a resource on the held first watch. The Kubernetes client then closes the watch with
     * an error other than 410 Gone. By default it would then stop the informer for good.
     */
    public void sendInvalidObjectOnHeldWatch() throws Exception {
        sendInvalidObject(heldWatch.get(20, TimeUnit.SECONDS));
    }

    /**
     * Sends an event that is not a resource on the watch that was opened last, after the events it was already sent.
     * The Kubernetes client then closes the watch with an error other than 410 Gone.
     */
    public void sendInvalidObjectOnLastOpenWatch() {
        WebSocket webSocket = lastOpenWatch;
        assertNotNull(webSocket, "no watch is open");
        sendInvalidObject(webSocket);
    }

    private static void sendInvalidObject(WebSocket webSocket) {
        Status status = new StatusBuilder().withCode(500).withMessage("not a resource").build();
        webSocket.send(SERIALIZATION.asJson(new WatchEvent(status, "MODIFIED")));
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        String path = request.getPath();
        boolean resources = "GET".equals(request.getMethod())
                && (path.equals(resourcesPath) || path.startsWith(resourcesPath + "?"));
        if (!resources) {
            return super.dispatch(request);
        }
        Integer code = failures.poll();
        if (code != null) {
            Status status = new StatusBuilder().withCode(code).withMessage("failed by the test").build();
            return new MockResponse().setResponseCode(code).setBody(SERIALIZATION.asJson(status));
        }
        if (!path.contains("watch=true")) {
            listRequests.release();
            MockResponse response = super.dispatch(request);
            long delay = nextListDelaySeconds;
            if (delay > 0) {
                nextListDelaySeconds = 0;
                response.setBodyDelay(delay, TimeUnit.SECONDS);
            }
            return response;
        }
        if (watches.incrementAndGet() == 1 && holdFirstWatch) {
            return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    heldWatch.complete(webSocket);
                    openWatches.release();
                }
            });
        }
        MockResponse response = super.dispatch(request);
        WebSocketListener listener = response.getWebSocketListener();
        if (listener != null) {
            response.withWebSocketUpgrade(new OpenSignalingListener(listener));
        }
        return response;
    }

    /**
     * Tells when a watch of the CRUD dispatcher is open, so an update made after that is sent to it.
     */
    private final class OpenSignalingListener extends WebSocketListener {

        private final WebSocketListener delegate;

        OpenSignalingListener(WebSocketListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onBeforeAccept(WebSocket webSocket, Response response) {
            delegate.onBeforeAccept(webSocket, response);
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            delegate.onOpen(webSocket, response);
            lastOpenWatch = webSocket;
            openWatches.release();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            delegate.onMessage(webSocket, text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            delegate.onMessage(webSocket, bytes);
        }

        @Override
        public void onMessage(WebSocket webSocket, byte[] bytes) {
            delegate.onMessage(webSocket, bytes);
        }

        @Override
        public void onPing(WebSocket webSocket, byte[] bytes) {
            delegate.onPing(webSocket, bytes);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            delegate.onClosing(webSocket, code, reason);
            closedWatches.release();
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            delegate.onClosed(webSocket, code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            delegate.onFailure(webSocket, t, response);
            // a watch the client drops without a close frame
            closedWatches.release();
        }
    }
}
