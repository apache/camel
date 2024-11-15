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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import io.fabric8.knative.eventing.v1.Trigger;
import io.fabric8.knative.messaging.v1.Subscription;
import io.fabric8.knative.sources.v1.SinkBinding;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
class KubernetesExportTest extends KubernetesExportBaseTest {

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.main),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.quarkus));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProject(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateKubernetesManifest(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        var labels = deployment.getMetadata().getLabels();
        var matchLabels = deployment.getSpec().getSelector().getMatchLabels();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, containers.size());
        Assertions.assertEquals("route", labels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("route", containers.get(0).getName());
        Assertions.assertEquals("route", matchLabels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT", containers.get(0).getImage());

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddApplicationProperties(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.traits = new String[] {
                "camel.properties=[foo=bar, bar=baz]" };
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        var labels = deployment.getMetadata().getLabels();
        var matchLabels = deployment.getSpec().getSelector().getMatchLabels();
        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, containers.size());
        Assertions.assertEquals("route", labels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("route", containers.get(0).getName());
        Assertions.assertEquals("route", matchLabels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("camel-test/route:1.0-SNAPSHOT", containers.get(0).getImage());

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Properties applicationProperties = getApplicationProperties(workingDir);

        Assertions.assertEquals("bar", applicationProperties.get("foo"));
        Assertions.assertEquals("baz", applicationProperties.get("bar"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddDefaultServiceSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--trait", "service.type=NodePort",
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Deployment deployment = getDeployment(rt);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route:1.0-SNAPSHOT", container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Service service = getService(rt);
        List<ServicePort> ports = service.getSpec().getPorts();
        Assertions.assertEquals("route", service.getMetadata().getName());
        Assertions.assertEquals("NodePort", service.getSpec().getType());
        Assertions.assertEquals(1, ports.size());
        Assertions.assertEquals("http", ports.get(0).getName());
        Assertions.assertEquals(80, ports.get(0).getPort());
        Assertions.assertEquals("http", ports.get(0).getTargetPort().getStrVal());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddServiceSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--trait", "service.type=NodePort",
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Deployment deployment = getDeployment(rt);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Service service = getService(rt);
        List<ServicePort> ports = service.getSpec().getPorts();
        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals("NodePort", service.getSpec().getType());
        Assertions.assertEquals(1, ports.size());
        Assertions.assertEquals("http", ports.get(0).getName());
        Assertions.assertEquals(80, ports.get(0).getPort());
        Assertions.assertEquals("http", ports.get(0).getTargetPort().getStrVal());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddIngressSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--trait-profile", "kubernetes",
                "--trait", "ingress.enabled=true",
                "--trait", "ingress.host=example.com",
                "--trait", "ingress.path=/something(/|$)(.*)",
                "--trait", "ingress.pathType=ImplementationSpecific",
                "--trait", "ingress.annotations=nginx.ingress.kubernetes.io/rewrite-target=/$2",
                "--trait", "ingress.annotations=nginx.ingress.kubernetes.io/use-regex=true",
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));
        Assertions.assertFalse(hasRoute(rt));

        Deployment deployment = getDeployment(rt);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Ingress ingress = getIngress(rt);
        Assertions.assertEquals("route-service", ingress.getMetadata().getName());
        Assertions.assertEquals("example.com", ingress.getSpec().getRules().get(0).getHost());
        Assertions.assertEquals("/something(/|$)(.*)",
                ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPath());
        Assertions.assertEquals("ImplementationSpecific",
                ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPathType());
        Assertions.assertEquals("route-service",
                ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName());
        Assertions.assertEquals("http",
                ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getPort().getName());
        Assertions.assertEquals("/$2",
                ingress.getMetadata().getAnnotations().get("nginx.ingress.kubernetes.io/rewrite-target"));
        Assertions.assertEquals("true", ingress.getMetadata().getAnnotations().get("nginx.ingress.kubernetes.io/use-regex"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddRouteSpec(RuntimeType rt) throws Exception {
        String certificate = IOHelper.loadText(new FileInputStream("src/test/resources/route/tls.pem"));
        String key = IOHelper.loadText(new FileInputStream("src/test/resources/route/tls.key"));
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--trait-profile", "openshift",
                "--trait", "route.enabled=true",
                "--trait", "route.host=example.com",
                "--trait", "route.tls-termination=edge",
                "--trait", "route.tls-certificate=" + certificate,
                "--trait", "route.tls-key=" + key,
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));
        Assertions.assertFalse(hasIngress(rt));

        Deployment deployment = getDeployment(rt);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Route route = getRoute(rt);
        Assertions.assertEquals("route-service", route.getMetadata().getName());
        Assertions.assertEquals("example.com", route.getSpec().getHost());
        Assertions.assertEquals("edge", route.getSpec().getTls().getTermination());
        Assertions.assertEquals("route-service", route.getSpec().getTo().getName());
        Assertions.assertTrue(certificate.startsWith(route.getSpec().getTls().getCertificate()));
        Assertions.assertTrue(key.startsWith(route.getSpec().getTls().getKey()));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddContainerSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--gav=camel-test:route-service:1.0.0", "--runtime=" + rt.runtime());
        command.traits = new String[] {
                "container.port=8088",
                "container.port-name=custom",
                "container.service-port-name=custom-port",
                "container.image-pull-policy=IfNotPresent",
                "container.service-port=443",
                "container.request-cpu=5m",
                "container.request-memory=100Mi",
                "container.limit-cpu=0.5",
                "container.limit-memory=512Mi" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("camel-test/route-service:1.0.0",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals("IfNotPresent",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().size());
        Assertions.assertEquals("custom",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getName());
        Assertions.assertEquals(8088,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort());
        Assertions.assertEquals("5m",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getRequests().get("cpu")
                        .toString());
        Assertions.assertEquals("100Mi",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getRequests().get("memory")
                        .toString());
        Assertions.assertEquals("0.5",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getLimits().get("cpu")
                        .toString());
        Assertions.assertEquals("512Mi",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getLimits().get("memory")
                        .toString());

        Service service = getService(rt);
        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(1, service.getSpec().getPorts().size());
        Assertions.assertEquals("custom-port", service.getSpec().getPorts().get(0).getName());
        Assertions.assertEquals(443, service.getSpec().getPorts().get(0).getPort());
        Assertions.assertEquals("custom", service.getSpec().getPorts().get(0).getTargetPort().getStrVal());
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
        command.doCall();

        Assertions.assertFalse(hasService(rt));
        Assertions.assertTrue(hasKnativeService(rt));

        io.fabric8.knative.serving.v1.Service service = getResource(rt, io.fabric8.knative.serving.v1.Service.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative service in Kubernetes manifest"));

        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(3, service.getMetadata().getLabels().size());
        Assertions.assertEquals("route-service", service.getMetadata().getLabels().get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("true", service.getMetadata().getLabels().get("bindings.knative.dev/include"));
        Assertions.assertEquals("cluster-local", service.getMetadata().getLabels().get("networking.knative.dev/visibility"));
        Assertions.assertEquals(1, service.getMetadata().getAnnotations().size());
        Assertions.assertEquals("60", service.getMetadata().getAnnotations().get("serving.knative.dev/rolloutDuration"));
        Assertions.assertEquals(1, service.getSpec().getTemplate().getMetadata().getLabels().size());
        Assertions.assertEquals("route-service",
                service.getSpec().getTemplate().getMetadata().getLabels().get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals(5, service.getSpec().getTemplate().getMetadata().getAnnotations().size());
        Assertions.assertEquals("cpu",
                service.getSpec().getTemplate().getMetadata().getAnnotations().get("autoscaling.knative.dev/metric"));
        Assertions.assertEquals("hpa.autoscaling.knative.dev",
                service.getSpec().getTemplate().getMetadata().getAnnotations().get("autoscaling.knative.dev/class"));
        Assertions.assertEquals("80",
                service.getSpec().getTemplate().getMetadata().getAnnotations().get("autoscaling.knative.dev/target"));
        Assertions.assertEquals("1",
                service.getSpec().getTemplate().getMetadata().getAnnotations().get("autoscaling.knative.dev/minScale"));
        Assertions.assertEquals("10",
                service.getSpec().getTemplate().getMetadata().getAnnotations().get("autoscaling.knative.dev/maxScale"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddKnativeTrigger(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:knative-event-source.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Trigger trigger = getResource(rt, Trigger.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative trigger in Kubernetes manifest"));

        Assertions.assertEquals("my-broker-knative-event-source-camel-event", trigger.getMetadata().getName());
        Assertions.assertEquals("my-broker", trigger.getSpec().getBroker());
        Assertions.assertEquals(1, trigger.getSpec().getFilter().getAttributes().size());
        Assertions.assertEquals("camel-event", trigger.getSpec().getFilter().getAttributes().get("type"));
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

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Subscription subscription = getResource(rt, Subscription.class)
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

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        SinkBinding sinkBinding = getResource(rt, SinkBinding.class)
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

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        SinkBinding sinkBinding = getResource(rt, SinkBinding.class)
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

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        SinkBinding sinkBinding = getResource(rt, SinkBinding.class)
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

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddVolumes(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.volumes = new String[] { "pvc-foo:/container/path/foo", "pvc-bar:/container/path/bar" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(2,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().size());
        Assertions.assertEquals("pvc-foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/container/path/foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("pvc-bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(1).getName());
        Assertions.assertEquals("/container/path/bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(1).getMountPath());
        Assertions.assertEquals(2, deployment.getSpec().getTemplate().getSpec().getVolumes().size());
        Assertions.assertEquals("pvc-foo", deployment.getSpec().getTemplate().getSpec().getVolumes().get(0).getName());
        Assertions.assertEquals("pvc-foo",
                deployment.getSpec().getTemplate().getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName());
        Assertions.assertEquals("pvc-bar", deployment.getSpec().getTemplate().getSpec().getVolumes().get(1).getName());
        Assertions.assertEquals("pvc-bar",
                deployment.getSpec().getTemplate().getSpec().getVolumes().get(1).getPersistentVolumeClaim().getClaimName());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddEnvVars(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.envVars = new String[] { "CAMEL_FOO=bar", "MY_ENV=foo" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(2, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().size());
        Assertions.assertEquals("CAMEL_FOO",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getName());
        Assertions.assertEquals("bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getValue());
        Assertions.assertEquals("MY_ENV",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(1).getName());
        Assertions.assertEquals("foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(1).getValue());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddAnnotations(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.annotations = new String[] { "foo=bar" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getMetadata().getAnnotations().size());
        Assertions.assertEquals("bar", deployment.getMetadata().getAnnotations().get("foo"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddLabels(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--label=foo=bar", "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        var labels = deployment.getMetadata().getLabels();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(3, labels.size());
        Assertions.assertEquals("camel", labels.get("app.kubernetes.io/runtime"));
        Assertions.assertEquals("route", labels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("bar", labels.get("foo"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddConfigs(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.configs = new String[] { "secret:foo", "configmap:bar" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(2,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().size());
        Assertions.assertEquals("foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_secrets/foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath());
        Assertions.assertTrue(
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getReadOnly());
        Assertions.assertEquals("bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(1).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(1).getMountPath());
        Assertions.assertTrue(
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(1).getReadOnly());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddResources(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.resources = new String[] { "configmap:foo/file.txt" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(1,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().size());
        Assertions.assertEquals("file",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/foo/file.txt",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("/file.txt",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getSubPath());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddOpenApis(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--runtime=" + rt.runtime(),
                "--open-api=configmap:openapi/spec.yaml");
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(1,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().size());
        Assertions.assertEquals("spec",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/openapi/spec.yaml",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("/spec.yaml",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getSubPath());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldUseImage(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.image = "quay.io/camel/demo-app:1.0";
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("demo-app", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel/demo-app:1.0",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    private Deployment getDeployment(RuntimeType rt) throws IOException {
        return getResource(rt, Deployment.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find deployment for: %s".formatted(rt.runtime())));
    }

    private Service getService(RuntimeType rt) throws IOException {
        return getResource(rt, Service.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find service for: %s".formatted(rt.runtime())));
    }

    private Ingress getIngress(RuntimeType rt) throws IOException {
        return getResource(rt, Ingress.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find ingress for: %s".formatted(rt.runtime())));
    }

    private boolean hasIngress(RuntimeType rt) throws IOException {
        return getResource(rt, Ingress.class).isPresent();
    }

    private Route getRoute(RuntimeType rt) throws IOException {
        return getResource(rt, Route.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find route for: %s".formatted(rt.runtime())));
    }

    private boolean hasRoute(RuntimeType rt) throws IOException {
        return getResource(rt, Route.class).isPresent();
    }

    private boolean hasService(RuntimeType rt) throws IOException {
        return getResource(rt, Service.class).isPresent();
    }

    private boolean hasKnativeService(RuntimeType rt) throws IOException {
        return getResource(rt, io.fabric8.knative.serving.v1.Service.class).isPresent();
    }

    private <T extends HasMetadata> Optional<T> getResource(RuntimeType rt, Class<T> type) throws IOException {
        if (rt == RuntimeType.quarkus) {
            try (FileInputStream fis
                    = new FileInputStream(
                            KubernetesHelper.getKubernetesManifest(ClusterType.KUBERNETES.name(),
                                    new File(workingDir, "/src/main/kubernetes")))) {
                List<HasMetadata> resources = kubernetesClient.load(fis).items();
                return resources.stream()
                        .filter(it -> type.isAssignableFrom(it.getClass()))
                        .map(type::cast)
                        .findFirst();
            }
        }
        if (rt == RuntimeType.springBoot || rt == RuntimeType.main) {
            var kind = type.getSimpleName().toLowerCase();
            File file = new File(workingDir, "src/main/jkube/%s.yml".formatted(kind));
            if (file.isFile()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    List<HasMetadata> resources = kubernetesClient.load(fis).items();
                    return resources.stream()
                            .filter(it -> type.isAssignableFrom(it.getClass()))
                            .map(type::cast)
                            .findFirst();
                }
            }
        }
        return Optional.empty();
    }

    protected String readResource(File workingDir, String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(workingDir.toPath().resolve(path).toFile())) {
            return IOHelper.loadText(fis);
        }
    }

    protected Properties getApplicationProperties(File workingDir) throws IOException {
        String content = readResource(workingDir, "src/main/resources/application.properties");
        Properties applicationProperties = new Properties();
        applicationProperties.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return applicationProperties;
    }

    private String getKnativeResourceConfiguration(File workingDir) throws IOException {
        return readResource(workingDir, "src/main/resources/knative.json");
    }

    private Model readMavenModel() throws Exception {
        File f = workingDir.toPath().resolve("pom.xml").toFile();
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }
}
