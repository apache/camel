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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
public class KubernetesNamespacesConsumerClusterwideIT extends KubernetesConsumerTestSupport {
    @Test
    public void clusterWideTest() {
        createNamespace("ns1", null);
        createNamespace("ns2", null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final List<String> list = result.getExchanges().stream().map(ex -> ex.getIn().getBody(String.class)).toList();
            assertThat(list, allOf(hasItem(containsString("ns1")), hasItem(containsString("ns2"))));
        });
    }

    @AfterEach
    public void cleanup() {
        List.of("ns1", "ns2").forEach(ns -> {
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
                fromF("kubernetes-namespaces://%s?oauthToken=%s", host, authToken)
                        .process(new KubernetesProcessor())
                        .to(result);
            }
        };
    }
}
