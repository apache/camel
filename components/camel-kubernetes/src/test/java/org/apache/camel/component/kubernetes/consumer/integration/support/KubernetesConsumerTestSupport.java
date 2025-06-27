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
package org.apache.camel.component.kubernetes.consumer.integration.support;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.MicroTimeBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
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

public class KubernetesConsumerTestSupport extends KubernetesTestSupport {
    protected static final KubernetesClient CLIENT = new KubernetesClientBuilder().build();

    protected static String ns1;
    protected static String ns2;

    protected static final Map<String, String> LABELS = Map.of("testkey", "testvalue");
    protected static final String WATCH_RESOURCE_NAME = "watch-resource";

    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @BeforeAll
    public static void createNamespaces() {
        ns1 = "kubernetesitcase-" + ThreadLocalRandom.current().nextInt(0, 100000);
        ns2 = "kubernetesitcase-" + ThreadLocalRandom.current().nextInt(0, 100000);
        CLIENT.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(ns1).endMetadata().build())
                .serverSideApply();
        CLIENT.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(ns2).endMetadata().build())
                .serverSideApply();
    }

    @AfterAll
    public static void deleteNamespaces() {
        CLIENT.namespaces().withName(ns1).delete();
        CLIENT.namespaces().withName(ns2).delete();
    }

    @AfterEach
    public void cleanup() {
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

    protected void createDeployment(String namespace, String name, Map<String, String> labels) {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withReplicas(0)
                .withNewSelector()
                .addToMatchLabels("app", "test")
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", "test")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("test")
                // Does not matter, we don't start the deployment (replicas == 0)
                .withImage("test:1.0")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        CLIENT.apps().deployments().inNamespace(namespace).resource(deployment).serverSideApply();
        assertNotNull(CLIENT.apps().deployments().inNamespace(namespace).withName(name).get());
    }

    protected void createEvent(String namespace, String name, Map<String, String> labels) {
        Event event = new EventBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withReason("Reason")
                .withType("Normal")
                .withEventTime(new MicroTimeBuilder().withTime("2025-01-01T00:00:00.000000Z").build())
                .withNewRegarding()
                .withKind("Pod")
                .withName("test")
                .withNamespace(namespace)
                .endRegarding()
                .withReportingController("ReportingController")
                .withReportingInstance("ReportingInstance")
                .withAction("Action")
                .build();

        CLIENT.events().v1().events().inNamespace(namespace).resource(event).serverSideApply();
        assertNotNull(CLIENT.events().v1().events().inNamespace(namespace).withName(name).get());
    }

    protected void createHPA(String namespace, String name, Map<String, String> labels) {
        HorizontalPodAutoscaler hpa = new HorizontalPodAutoscalerBuilder()
                .withNewMetadata().withName(name).withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withMinReplicas(1)
                .withMaxReplicas(5)
                .withTargetCPUUtilizationPercentage(50)
                .withNewScaleTargetRef()
                .withApiVersion("apps/v1")
                .withKind("Deployment")
                .withName("test")
                .endScaleTargetRef()
                .endSpec()
                .build();

        CLIENT.autoscaling().v1().horizontalPodAutoscalers().inNamespace(namespace).resource(hpa).serverSideApply();
        assertNotNull(CLIENT.autoscaling().v1().horizontalPodAutoscalers().inNamespace(namespace).withName(name).get());
    }

    protected void createNamespace(String name, Map<String, String> labels) {
        Namespace namespace = new NamespaceBuilder()
                .withNewMetadata().withName(name).withLabels(labels)
                .endMetadata()
                .build();

        CLIENT.namespaces().resource(namespace).serverSideApply();
        assertNotNull(CLIENT.namespaces().withName(name).get());
    }

    protected void createPod(String namespace, String name, Map<String, String> labels) {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName(name)
                .withImage("busybox")
                .endContainer()
                .endSpec()
                .build();

        CLIENT.pods().inNamespace(namespace).resource(pod).serverSideApply();
        assertNotNull(CLIENT.pods().inNamespace(namespace).withName(name).get());
        // Also wait until the pod completes, otherwise it may prevent ns from being deleted if deleted too quickly
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> {
            Pod p = CLIENT.pods().inNamespace(namespace).withName(name).get();
            return p.getStatus() != null && "Succeeded".equals(p.getStatus().getPhase());
        });
    }

    protected void createReplicationController(String namespace, String name, Map<String, String> labels) {
        ReplicationController rc = new ReplicationControllerBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withReplicas(0)
                .withSelector(Map.of("key", "value"))
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(Map.of("key", "value"))
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage("busybox")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        CLIENT.replicationControllers().inNamespace(namespace).resource(rc).serverSideApply();
        assertNotNull(CLIENT.replicationControllers().inNamespace(namespace).withName(name).get());
    }

    protected void createService(String namespace, String name, Map<String, String> labels) {
        Service svc = new ServiceBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewPort()
                .withProtocol("TCP")
                .withPort(80)
                .withTargetPort(new IntOrString(8080))
                .endPort()
                .withSelector(Map.of("test", "test"))
                .withType("ClusterIP")
                .endSpec()
                .build();

        CLIENT.services().inNamespace(namespace).resource(svc).serverSideApply();
        assertNotNull(CLIENT.services().inNamespace(namespace).withName(name).get());
    }

    public static class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            final Message in = exchange.getIn();
            HasMetadata res = in.getBody(HasMetadata.class);
            StringBuilder body = new StringBuilder(res.getKind()).append(" ").append(res.getMetadata().getName());
            String ns = res.getMetadata().getNamespace();
            if (ns != null) {
                body.append(" ").append(ns);
            }
            body.append(" ").append(in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
            exchange.getIn().setBody(body.toString());
        }
    }
}
