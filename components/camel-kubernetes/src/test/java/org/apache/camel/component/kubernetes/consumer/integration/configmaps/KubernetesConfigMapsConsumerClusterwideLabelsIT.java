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

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
public class KubernetesConfigMapsConsumerClusterwideLabelsIT extends KubernetesConfigMapsTestSupport {
    private static final Map<String, String> LABELS = Map.of("testkey", "testvalue");

    @Test
    public void clusterWideLabelsTest() throws Exception {
        final String withLabels = "cm-with-labels";
        result.expectedBodiesReceivedInAnyOrder(
                withLabels + " " + NS_DEFAULT + " ADDED",
                withLabels + " " + NS_WATCH + " ADDED");

        // Create the resource in two namespaces, it should list only from one
        createConfigMap(NS_DEFAULT, withLabels, LABELS);
        createConfigMap(NS_WATCH, withLabels, LABELS);
        createConfigMap(NS_WATCH, "different", Map.of("otherKey", "otherValue"));

        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kubernetes-config-maps://%s?oauthToken=%s&labelKey=%s&labelValue=%s",
                        host, authToken, "testkey", "testvalue")
                        .process(new KubernetesProcessor())
                        .to(result);
            }
        };

    }
}
