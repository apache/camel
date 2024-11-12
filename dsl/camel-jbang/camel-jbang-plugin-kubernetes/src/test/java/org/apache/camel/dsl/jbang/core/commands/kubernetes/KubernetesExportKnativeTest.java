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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import io.fabric8.knative.eventing.v1.Trigger;
import io.fabric8.knative.messaging.v1.Subscription;
import io.fabric8.knative.sources.v1.SinkBinding;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
public class KubernetesExportKnativeTest extends KubernetesExportBaseTest {

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.main),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.quarkus));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeServiceSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());

        command.traits = new String[] {
                "knative-service.enabled=true",
                "knative-service.class=hpa.autoscaling.knative.dev",
                "knative-service.autoscaling-metric=cpu",
                "knative-service.autoscaling-target=80",
                "knative-service.min-scale=1",
                "knative-service.max-scale=10",
                "knative-service.rollout-duration=60",
                "knative-service.visibility=cluster-local" };
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertFalse(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertTrue(hasKnativeService(rt, ClusterType.KUBERNETES));

        io.fabric8.knative.serving.v1.Service service
                = getResource(rt, ClusterType.KUBERNETES, io.fabric8.knative.serving.v1.Service.class)
                        .orElseThrow(() -> new RuntimeCamelException("Missing Knative service in Kubernetes manifest"));

        Map<String, String> labelsA = service.getMetadata().getLabels();
        var labelsB = service.getSpec().getTemplate().getMetadata().getLabels();
        Map<String, String> annotations = service.getSpec().getTemplate().getMetadata().getAnnotations();
        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(3, labelsA.size());
        Assertions.assertEquals("route-service", labelsA.get(BaseTrait.KUBERNETES_NAME_LABEL));
        Assertions.assertEquals("true", labelsA.get("bindings.knative.dev/include"));
        Assertions.assertEquals("cluster-local", labelsA.get("networking.knative.dev/visibility"));
        Assertions.assertEquals(1, service.getMetadata().getAnnotations().size());
        Assertions.assertEquals("60", service.getMetadata().getAnnotations().get("serving.knative.dev/rolloutDuration"));
        Assertions.assertEquals(1, labelsB.size());
        Assertions.assertEquals("route-service", labelsB.get(BaseTrait.KUBERNETES_NAME_LABEL));
        Assertions.assertEquals(5, annotations.size());
        Assertions.assertEquals("cpu", annotations.get("autoscaling.knative.dev/metric"));
        Assertions.assertEquals("hpa.autoscaling.knative.dev", annotations.get("autoscaling.knative.dev/class"));
        Assertions.assertEquals("80", annotations.get("autoscaling.knative.dev/target"));
        Assertions.assertEquals("1", annotations.get("autoscaling.knative.dev/minScale"));
        Assertions.assertEquals("10", annotations.get("autoscaling.knative.dev/maxScale"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeTrigger(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-event-source.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.traits = new String[] {
                "knative.filters=source=my-source" };
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertFalse(hasKnativeService(rt, ClusterType.KUBERNETES));

        Trigger trigger = getResource(rt, ClusterType.KUBERNETES, Trigger.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative trigger in Kubernetes manifest"));

        Assertions.assertEquals("my-broker-knative-event-source-camel-event", trigger.getMetadata().getName());
        Assertions.assertEquals("my-broker", trigger.getSpec().getBroker());
        Assertions.assertEquals(2, trigger.getSpec().getFilter().getAttributes().size());
        Assertions.assertEquals("camel-event", trigger.getSpec().getFilter().getAttributes().get("type"));
        Assertions.assertEquals("my-source", trigger.getSpec().getFilter().getAttributes().get("source"));
        Assertions.assertEquals("knative-event-source", trigger.getSpec().getSubscriber().getRef().getName());
        Assertions.assertEquals("Service", trigger.getSpec().getSubscriber().getRef().getKind());
        Assertions.assertEquals("v1", trigger.getSpec().getSubscriber().getRef().getApiVersion());
        Assertions.assertEquals("/events/camel-event", trigger.getSpec().getSubscriber().getUri());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("classpath:knative.json", applicationProperties.get("camel.component.knative.environmentPath"));

        Assertions.assertEquals("""
                {
                  "resources" : [ {
                    "name" : "camel-event",
                    "type" : "event",
                    "endpointKind" : "source",
                    "path" : "/events/camel-event",
                    "objectApiVersion" : "eventing.knative.dev/v1",
                    "objectKind" : "Broker",
                    "objectName" : "my-broker",
                    "reply" : false
                  } ]
                }
                """, getKnativeResourceConfiguration(workingDir));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeSubscription(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-channel-source.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertFalse(hasKnativeService(rt, ClusterType.KUBERNETES));

        Subscription subscription = getResource(rt, ClusterType.KUBERNETES, Subscription.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative subscription in Kubernetes manifest"));

        Assertions.assertEquals("my-channel-knative-channel-source", subscription.getMetadata().getName());
        Assertions.assertEquals("my-channel", subscription.getSpec().getChannel().getName());
        Assertions.assertEquals("knative-channel-source", subscription.getSpec().getSubscriber().getRef().getName());
        Assertions.assertEquals("Service", subscription.getSpec().getSubscriber().getRef().getKind());
        Assertions.assertEquals("v1", subscription.getSpec().getSubscriber().getRef().getApiVersion());
        Assertions.assertEquals("/channels/my-channel", subscription.getSpec().getSubscriber().getUri());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("classpath:knative.json", applicationProperties.get("camel.component.knative.environmentPath"));

        Assertions.assertEquals("""
                {
                  "resources" : [ {
                    "name" : "my-channel",
                    "type" : "channel",
                    "endpointKind" : "source",
                    "path" : "/channels/my-channel",
                    "objectApiVersion" : "messaging.knative.dev/v1",
                    "objectKind" : "Channel",
                    "objectName" : "my-channel",
                    "reply" : false
                  } ]
                }
                """, getKnativeResourceConfiguration(workingDir));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeBrokerSinkBinding(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-event-sink.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertFalse(hasKnativeService(rt, ClusterType.KUBERNETES));

        SinkBinding sinkBinding = getResource(rt, ClusterType.KUBERNETES, SinkBinding.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative sinkBinding in Kubernetes manifest"));

        Assertions.assertEquals("knative-event-sink", sinkBinding.getMetadata().getName());
        Assertions.assertEquals("my-broker", sinkBinding.getSpec().getSink().getRef().getName());
        Assertions.assertEquals("Broker", sinkBinding.getSpec().getSink().getRef().getKind());
        Assertions.assertEquals("eventing.knative.dev/v1", sinkBinding.getSpec().getSink().getRef().getApiVersion());
        Assertions.assertEquals("knative-event-sink", sinkBinding.getSpec().getSubject().getName());
        Assertions.assertEquals("Deployment", sinkBinding.getSpec().getSubject().getKind());
        Assertions.assertEquals("apps/v1", sinkBinding.getSpec().getSubject().getApiVersion());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("classpath:knative.json", applicationProperties.get("camel.component.knative.environmentPath"));

        Assertions.assertEquals("""
                {
                  "resources" : [ {
                    "name" : "my-broker",
                    "type" : "event",
                    "endpointKind" : "sink",
                    "url" : "{{k.sink:http://localhost:8080}}",
                    "objectApiVersion" : "eventing.knative.dev/v1",
                    "objectKind" : "Broker",
                    "objectName" : "my-broker",
                    "reply" : false
                  } ]
                }
                """, getKnativeResourceConfiguration(workingDir));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeChannelSinkBinding(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-channel-sink.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertFalse(hasKnativeService(rt, ClusterType.KUBERNETES));

        SinkBinding sinkBinding = getResource(rt, ClusterType.KUBERNETES, SinkBinding.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative sinkBinding in Kubernetes manifest"));

        Assertions.assertEquals("knative-channel-sink", sinkBinding.getMetadata().getName());
        Assertions.assertEquals("my-channel", sinkBinding.getSpec().getSink().getRef().getName());
        Assertions.assertEquals("Channel", sinkBinding.getSpec().getSink().getRef().getKind());
        Assertions.assertEquals("messaging.knative.dev/v1", sinkBinding.getSpec().getSink().getRef().getApiVersion());
        Assertions.assertEquals("knative-channel-sink", sinkBinding.getSpec().getSubject().getName());
        Assertions.assertEquals("Deployment", sinkBinding.getSpec().getSubject().getKind());
        Assertions.assertEquals("apps/v1", sinkBinding.getSpec().getSubject().getApiVersion());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("classpath:knative.json", applicationProperties.get("camel.component.knative.environmentPath"));

        Assertions.assertEquals("""
                {
                  "resources" : [ {
                    "name" : "my-channel",
                    "type" : "channel",
                    "endpointKind" : "sink",
                    "url" : "{{k.sink:http://localhost:8080}}",
                    "objectApiVersion" : "messaging.knative.dev/v1",
                    "objectKind" : "Channel",
                    "objectName" : "my-channel",
                    "reply" : false
                  } ]
                }
                """, getKnativeResourceConfiguration(workingDir));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeEndpointSinkBinding(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-endpoint-sink.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(rt, ClusterType.KUBERNETES));
        Assertions.assertFalse(hasKnativeService(rt, ClusterType.KUBERNETES));

        SinkBinding sinkBinding = getResource(rt, ClusterType.KUBERNETES, SinkBinding.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative sinkBinding in Kubernetes manifest"));

        Assertions.assertEquals("knative-endpoint-sink", sinkBinding.getMetadata().getName());
        Assertions.assertEquals("my-endpoint", sinkBinding.getSpec().getSink().getRef().getName());
        Assertions.assertEquals("Service", sinkBinding.getSpec().getSink().getRef().getKind());
        Assertions.assertEquals("serving.knative.dev/v1", sinkBinding.getSpec().getSink().getRef().getApiVersion());
        Assertions.assertEquals("knative-endpoint-sink", sinkBinding.getSpec().getSubject().getName());
        Assertions.assertEquals("Deployment", sinkBinding.getSpec().getSubject().getKind());
        Assertions.assertEquals("apps/v1", sinkBinding.getSpec().getSubject().getApiVersion());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("classpath:knative.json", applicationProperties.get("camel.component.knative.environmentPath"));

        Assertions.assertEquals("""
                {
                  "resources" : [ {
                    "name" : "my-endpoint",
                    "type" : "endpoint",
                    "endpointKind" : "sink",
                    "url" : "{{k.sink:http://localhost:8080}}",
                    "objectApiVersion" : "serving.knative.dev/v1",
                    "objectKind" : "Service",
                    "objectName" : "my-endpoint",
                    "reply" : false
                  } ]
                }
                """, getKnativeResourceConfiguration(workingDir));
    }

    private String getKnativeResourceConfiguration(File workingDir) throws IOException {
        return readResource(workingDir, "src/main/resources/knative.json");
    }
}
