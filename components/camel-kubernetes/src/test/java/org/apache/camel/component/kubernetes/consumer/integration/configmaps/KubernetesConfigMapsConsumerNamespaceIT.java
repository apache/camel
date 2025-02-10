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
package org.apache.camel.component.kubernetes.consumer.integration.configmaps;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
public class KubernetesConfigMapsConsumerNamespaceIT extends KubernetesConfigMapsTestSupport {
    private static final String CM1 = "cm1";
    private static final String CM2 = "cm2";

    @Test
    public void namespaceTest() {
        // Create the resource in two namespaces, it should list only from one
        createConfigMap(NS_DEFAULT, CM1, null);
        createConfigMap(NS_WATCH, CM2, null);

        // Wait until there is an event for cm2 and no event for cm1
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final List<String> list = result.getExchanges().stream().map(ex -> ex.getIn().getBody(String.class)).toList();
            assertThat(list, hasItem(containsString(CM2)));
            assertThat(list, everyItem(not(containsString(CM1))));
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kubernetes-config-maps://%s?oauthToken=%s&namespace=%s", host, authToken, NS_WATCH)
                        .process(new KubernetesProcessor())
                        .to(result);
            }
        };
    }
}
