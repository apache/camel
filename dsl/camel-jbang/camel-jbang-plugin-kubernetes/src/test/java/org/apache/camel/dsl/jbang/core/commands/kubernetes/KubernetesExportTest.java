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

import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
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

        Properties props = model.getProperties();
        Assertions.assertEquals("examples/route:1.0.0", props.get("jkube.image.name"));
        Assertions.assertEquals("eclipse-temurin:17", props.get("jkube.base.image"));
        Assertions.assertEquals("jib", props.get("jkube.build.strategy"));
        Assertions.assertNull(props.get("jkube.docker.push.registry"));
        Assertions.assertNull(props.get("jkube.container-image.registry"));
        Assertions.assertNull(props.get("jkube.container-image.platforms"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateNamedProject(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--name=projName", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("proj-name", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldConfigureContainerImage(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-riders", "--base-image=my-base-image:latest",
                "--registry-mirror=mirror.gcr.io", "--image-builder=docker",
                "--image-platform=linux/amd64", "--image-platform=linux/arm64", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("quay.io/camel-riders/route:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("mirror.gcr.io/my-base-image:latest", props.get("jkube.base.image"));
        Assertions.assertEquals("docker", props.get("jkube.build.strategy"));
        Assertions.assertEquals("quay.io", props.get("jkube.docker.push.registry"));
        Assertions.assertEquals("quay.io", props.get("jkube.container-image.registry"));
        Assertions.assertEquals("linux/amd64,linux/arm64", props.get("jkube.container-image.platforms"));
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
                "--cluster-type", "kubernetes",
                "--trait", "ingress.enabled=true",
                "--trait", "ingress.host=example.com",
                "--trait", "ingress.path=/something(/|$)(.*)",
                "--trait", "ingress.pathType=ImplementationSpecific",
                "--trait", "ingress.annotations=nginx.ingress.kubernetes.io/rewrite-target=/$2",
                "--trait", "ingress.annotations=nginx.ingress.kubernetes.io/use-regex=true",
                "--trait", "ingress.tls-hosts=acme.com,acme2.com",
                "--trait", "ingress.tls-secret-name=acme-tls-secret",
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
        Assertions.assertTrue(ingress.getSpec().getTls().get(0).getHosts().contains("acme.com"));
        Assertions.assertTrue(ingress.getSpec().getTls().get(0).getHosts().contains("acme2.com"));
        Assertions.assertEquals("acme-tls-secret", ingress.getSpec().getTls().get(0).getSecretName());

    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddRouteSpec(RuntimeType rt) throws Exception {
        String certificate = IOHelper.loadText(new FileInputStream("src/test/resources/route/tls.pem"));
        String key = IOHelper.loadText(new FileInputStream("src/test/resources/route/tls.key"));
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--cluster-type", "openshift",
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
}
