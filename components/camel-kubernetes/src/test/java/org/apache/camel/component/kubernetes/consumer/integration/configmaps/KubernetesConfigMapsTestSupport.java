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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KubernetesConfigMapsTestSupport extends KubernetesTestSupport {
    protected static final KubernetesClient CLIENT = new KubernetesClientBuilder().build();

    protected static final String NS_DEFAULT = "kubernetescm-it";
    protected static final String NS_WATCH = "kubernetescm-watch";

    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @BeforeAll
    public static void createNamespaces() {
        CLIENT.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(NS_DEFAULT).endMetadata().build())
                .serverSideApply();
        CLIENT.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(NS_WATCH).endMetadata().build())
                .serverSideApply();
    }

    @AfterAll
    public static void deleteNamespaces() {
        CLIENT.namespaces().withName(NS_DEFAULT).delete();
        CLIENT.namespaces().withName(NS_WATCH).delete();

        List.of(NS_DEFAULT, NS_WATCH).forEach(ns -> Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> CLIENT.namespaces().withName(ns).get() == null));
    }

    @AfterEach
    public void cleanup() {
        CLIENT.configMaps().inNamespace(NS_DEFAULT).delete();
        CLIENT.configMaps().inNamespace(NS_WATCH).delete();
        result.reset();
    }

    protected void createConfigMap(String namespace, String name, Map<String, String> labels) {
        Map<String, String> data = Map.of("test1", "test1");

        ConfigMap cm = new ConfigMapBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withData(data)
                .build();

        CLIENT.configMaps().inNamespace(namespace).resource(cm).serverSideApply();
        assertNotNull(CLIENT.configMaps().inNamespace(namespace).withName(name).get());
    }

    public static class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            final Message in = exchange.getIn();
            ConfigMap cm = in.getBody(ConfigMap.class);
            exchange.getIn().setBody(cm.getMetadata().getName() + " " + cm.getMetadata().getNamespace() + " "
                                     + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
