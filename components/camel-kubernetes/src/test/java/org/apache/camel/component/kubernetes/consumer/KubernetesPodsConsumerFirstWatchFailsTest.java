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
package org.apache.camel.component.kubernetes.consumer;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the first watch of a consumer cannot be created (the API server cannot be reached, or answers 403 because the
 * RBAC rules of the service account are not there yet), the consumer must try again, as it does when the client closes
 * a watch with an error.
 */
@EnableKubernetesMockClient
class KubernetesPodsConsumerFirstWatchFailsTest extends KubernetesTestSupport {

    private static final String WATCH_PATH = "/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true";

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testWatchAgainWhenTheFirstWatchFails() throws Exception {
        // the first watch fails
        server.expect().withPath(WATCH_PATH)
                .andReturn(403, new StatusBuilder().withCode(403).withReason("Forbidden").withMessage("forbidden").build())
                .once();
        server.expect().withPath(WATCH_PATH)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10)
                .andEmit(new WatchEvent(
                        new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test")
                                .withResourceVersion("2").endMetadata().build(),
                        "ADDED"))
                .done()
                .once();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedMessagesMatches(e -> "pod1".equals(
                e.getMessage().getBody(Pod.class).getMetadata().getName()));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kubernetes-pods://kubernetes?kubernetesClient=#kubernetesClient&namespace=test").routeId("pods")
                        .to("mock:result");
            }
        });
        context.start();

        // watched again after 1 second
        mock.setResultWaitTime(10000);
        mock.assertIsSatisfied();
        assertEquals(2, server.getRequestCount());
        assertTrue(context.getRouteController().getRouteStatus("pods").isStarted());
    }
}
