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
package org.apache.camel.component.kubernetes.consumer.integration.namespaces;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.consumer.integration.support.KubernetesConsumerTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
public class KubernetesNamespacesConsumerResourceNameIT extends KubernetesConsumerTestSupport {
    @Test
    public void resourceNameTest() throws Exception {
        result.expectedBodiesReceived("Namespace " + WATCH_RESOURCE_NAME + " ADDED");

        createNamespace(WATCH_RESOURCE_NAME, null);
        createNamespace("ns1", null);

        result.assertIsSatisfied();
    }

    @AfterEach
    public void cleanup() {
        List.of(WATCH_RESOURCE_NAME, "ns1").forEach(ns -> {
            CLIENT.namespaces().withName(ns).delete();
            Awaitility.await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> CLIENT.namespaces().withName(ns).get() == null);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kubernetes-namespaces://%s?oauthToken=%s&resourceName=%s&namespace=%s", host, authToken,
                        WATCH_RESOURCE_NAME,
                        ns2)
                        .process(new KubernetesProcessor())
                        .to(result);
            }
        };
    }
}
