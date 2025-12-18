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
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
        // the backslash is to simulate the windows file separator, related to CAMEL-22776
        // as the ExportBaseCommand uses Paths.get to read the file it also sets the backslash
        KubernetesExport command = createCommand(new String[] { "classpath:myapp\\route.yaml" },
                "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route:1.0.0", props.get("jkube.image.name"));
        Assertions.assertEquals("route:1.0.0", props.get("jkube.container-image.name"));
        Assertions.assertEquals("mirror.gcr.io/library/eclipse-temurin:21", props.get("jkube.container-image.from"));
        Assertions.assertEquals("jib", props.get("jkube.build.strategy"));
        Assertions.assertNull(props.get("jkube.docker.push.registry"));
        Assertions.assertNull(props.get("jkube.container-image.registry"));
        Assertions.assertNull(props.get("jkube.container-image.platforms"));

        Properties applicationProperties = getApplicationProperties(workingDir);
        String scriptContent = readResource(workingDir, "src/main/scripts/run-java.sh");
        Assertions.assertNotNull(scriptContent);
        Assertions.assertTrue(scriptContent.length() > 0);

        if (RuntimeType.quarkus == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals("9876", applicationProperties.get("quarkus.management.port"));
            Assertions.assertEquals("9876", props.get("jkube.enricher.jkube-healthcheck-quarkus.port"));
            Assertions.assertEquals("/observe/health", props.get("quarkus.smallrye-health.root-path"));
        } else if (RuntimeType.springBoot == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals("9876", applicationProperties.get("management.server.port"));
            Assertions.assertEquals("/observe", applicationProperties.get("management.endpoints.web.base-path"));
            Assertions.assertEquals("true", applicationProperties.get("management.health.probes.enabled"));
        } else if (RuntimeType.main == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals("9876", applicationProperties.get("camel.management.port"));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateJava17Project(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime(), "--java-version=17");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route:1.0.0", props.get("jkube.image.name"));
        Assertions.assertEquals("route:1.0.0", props.get("jkube.container-image.name"));
        Assertions.assertEquals("mirror.gcr.io/library/eclipse-temurin:17", props.get("jkube.container-image.from"));
        Assertions.assertEquals("jib", props.get("jkube.build.strategy"));
        Assertions.assertNull(props.get("jkube.docker.push.registry"));
        Assertions.assertNull(props.get("jkube.container-image.registry"));
        Assertions.assertNull(props.get("jkube.container-image.platforms"));

        if (RuntimeType.quarkus == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals("/observe/health", props.get("quarkus.smallrye-health.root-path"));
        }
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
    public void shouldGenerateProjectFromDir(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "src/test/resources/myapp" },
                "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route:1.0.0", props.get("jkube.image.name"));
        Assertions.assertEquals("route:1.0.0", props.get("jkube.container-image.name"));
        Assertions.assertEquals("mirror.gcr.io/library/eclipse-temurin:21", props.get("jkube.container-image.from"));
        Assertions.assertEquals("jib", props.get("jkube.build.strategy"));
        Assertions.assertNull(props.get("jkube.docker.push.registry"));
        Assertions.assertNull(props.get("jkube.container-image.registry"));
        Assertions.assertNull(props.get("jkube.container-image.platforms"));

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("MySuperApp", applicationProperties.getProperty("camel.main.name"));
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
        Assertions.assertEquals("quay.io/camel-riders/route:1.0-SNAPSHOT", props.get("jkube.container-image.name"));
        Assertions.assertEquals("mirror.gcr.io/my-base-image:latest", props.get("jkube.container-image.from"));
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
        Assertions.assertNull(containers.get(0).getImage());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateCronjobKubernetesManifest(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--runtime=" + rt.runtime(),
                "--service-account=my-svc-account");
        command.traits = new String[] {
                "cronjob.enabled=true",
                "cronjob.schedule=\"0 22 * * 1-5\"",
                "cronjob.timezone=Europe/Lisbon",
                "cronjob.startingDeadlineSeconds=2",
                "cronjob.activeDeadlineSeconds=3",
                "cronjob.backoffLimit=4",
                "cronjob.durationMaxIdleSeconds=5",
                "container.imagePullPolicy=Never"
        };
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        CronJob cronjob = getResource(rt, CronJob.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find cronjob for: %s".formatted(rt.runtime())));
        JobSpec jobSpec = cronjob.getSpec().getJobTemplate().getSpec();
        Assertions.assertEquals("route", cronjob.getMetadata().getName());
        Assertions.assertEquals("0 22 * * 1-5", cronjob.getSpec().getSchedule());
        Assertions.assertEquals("Europe/Lisbon", cronjob.getSpec().getTimeZone());
        Assertions.assertEquals(2, cronjob.getSpec().getStartingDeadlineSeconds());
        Assertions.assertEquals(3, jobSpec.getActiveDeadlineSeconds());
        Assertions.assertEquals(4, jobSpec.getBackoffLimit());
        Assertions.assertEquals("Never", jobSpec.getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
        Assertions.assertEquals("my-svc-account", jobSpec.getTemplate().getSpec().getServiceAccountName());

        Properties applicationProperties = getApplicationProperties(workingDir);
        Assertions.assertEquals("5", applicationProperties.getProperty("camel.main.duration-max-idle-seconds"));

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT", props.get("jkube.container-image.name"));
        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        // there are no health probes for cronjobs
        if (RuntimeType.quarkus == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertNull(applicationProperties.get("quarkus.management.port"));
            Assertions.assertNull(props.get("jkube.enricher.jkube-healthcheck-quarkus.port"));
            Assertions.assertNull(props.get("quarkus.smallrye-health.root-path"));
        } else if (RuntimeType.springBoot == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertNull(applicationProperties.get("management.server.port"));
            Assertions.assertNull(applicationProperties.get("management.endpoints.web.base-path"));
            Assertions.assertNull(applicationProperties.get("management.health.probes.enabled"));
        } else if (RuntimeType.main == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertNull(applicationProperties.get("camel.management.port"));
        }
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
        Assertions.assertNull(containers.get(0).getImage());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("camel-test/route:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("camel-test/route:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Properties applicationProperties = getApplicationProperties(workingDir);

        Assertions.assertEquals("bar", applicationProperties.get("foo"));
        Assertions.assertEquals("baz", applicationProperties.get("bar"));

        if (RuntimeType.springBoot == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals("/observe", applicationProperties.get("management.endpoints.web.base-path"));
            Assertions.assertEquals("true", applicationProperties.get("management.health.probes.enabled"));
        }
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
        Assertions.assertNull(container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("route:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

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
        Assertions.assertNull(container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route-service", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

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
        Assertions.assertNull(container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route-service", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

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
        Assertions.assertNull(container.getImage());
        Assertions.assertEquals(1, container.getPorts().size());
        Assertions.assertEquals("http", container.getPorts().get(0).getName());
        Assertions.assertEquals(8080, container.getPorts().get(0).getContainerPort());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route-service", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

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
    public void shouldAddJkubeOpenshiftDeploymentProperty(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--cluster-type", "openshift",
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Properties props = model.getProperties();
        Assertions.assertEquals("true", props.get("jkube.build.switchToDeployment"),
                "property jkube.build.switchToDeployment=true not set in pom.xml");
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
        Assertions.assertNull(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
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

        Model model = readMavenModel();
        Assertions.assertEquals("camel-test", model.getGroupId());
        Assertions.assertEquals("route-service", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route-service:1.0.0", props.get("jkube.image.name"));
        Assertions.assertEquals("route-service:1.0.0", props.get("jkube.container-image.name"));

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
        KubernetesExport command
                = createCommand(new String[] { "classpath:route.yaml", "src/test/resources/application.properties", },
                        "--runtime=" + rt.runtime());
        command.envVars = new String[] { "CAMEL_FOO=bar", "MY_ENV=foo" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(rt);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(4, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().size());
        Assertions.assertEquals("MY_VAR",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getName());
        Assertions.assertEquals("\"my value\"",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getValue());
        Assertions.assertEquals("MY_ENV",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(1).getName());
        Assertions.assertEquals("fuzz",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(1).getValue());
        Assertions.assertEquals("CAMEL_FOO",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(2).getName());
        Assertions.assertEquals("bar",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(2).getValue());
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

    @Test
    public void shouldAddConfigs() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + RuntimeType.main);
        command.configs = new String[] {
                "secret:foo", "secret:foo/key-foo", "configmap:bar", "configmap:bar/key-bar", "configmap:bar2/my.properties" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(RuntimeType.main);
        List<Volume> volumes = deployment.getSpec().getTemplate().getSpec().getVolumes();
        List<VolumeMount> volumeMounts = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(5, volumeMounts.size());
        // secret:foo
        Assertions.assertEquals("foo", volumeMounts.get(0).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_secrets/foo", volumeMounts.get(0).getMountPath());
        Assertions.assertTrue(volumeMounts.get(0).getReadOnly());
        // secret:foo/key-foo
        Assertions.assertEquals("foo", volumeMounts.get(1).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_secrets/foo/key-foo", volumeMounts.get(1).getMountPath());
        Assertions.assertTrue(volumeMounts.get(1).getReadOnly());
        Assertions.assertEquals("key-foo", volumes.get(1).getSecret().getItems().get(0).getKey());
        Assertions.assertEquals("key-foo", volumes.get(1).getSecret().getItems().get(0).getPath());
        // configmap:bar
        Assertions.assertEquals("bar", volumeMounts.get(2).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar", volumeMounts.get(2).getMountPath());
        Assertions.assertTrue(volumeMounts.get(2).getReadOnly());
        // configmap:bar/key-bar
        Assertions.assertEquals("bar", volumeMounts.get(3).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar/key-bar", volumeMounts.get(3).getMountPath());
        Assertions.assertTrue(volumeMounts.get(3).getReadOnly());
        Assertions.assertEquals("key-bar", volumes.get(3).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("key-bar", volumes.get(3).getConfigMap().getItems().get(0).getPath());
        // configmap:bar2/my.properties
        Assertions.assertEquals("bar2", volumeMounts.get(4).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar2/my.properties", volumeMounts.get(4).getMountPath());
        Assertions.assertEquals("my.properties", volumes.get(4).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("my.properties", volumes.get(4).getConfigMap().getItems().get(0).getPath());
    }

    @Test
    public void shouldAddResources() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + RuntimeType.main);
        command.resources = new String[] {
                "secret:foo", "secret:foo/key-foo", "secret:foo/key-foo@/etc/foodir/my-file.txt", "configmap:bar",
                "configmap:bar/key-bar", "configmap:bar2/my.properties@/var/dir1/bar.bin" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(RuntimeType.main);
        List<Volume> volumes = deployment.getSpec().getTemplate().getSpec().getVolumes();
        List<VolumeMount> volumeMounts = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(6, volumeMounts.size());
        // secret:foo
        Assertions.assertEquals("foo", volumeMounts.get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_secrets/foo", volumeMounts.get(0).getMountPath());
        Assertions.assertTrue(volumeMounts.get(0).getReadOnly());
        // secret:foo/key-foo
        Assertions.assertEquals("foo", volumeMounts.get(1).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_secrets/foo/key-foo", volumeMounts.get(1).getMountPath());
        Assertions.assertEquals("key-foo", volumes.get(1).getSecret().getItems().get(0).getKey());
        Assertions.assertEquals("key-foo", volumes.get(1).getSecret().getItems().get(0).getPath());
        // secret:foo/key-foo@/etc/foodir/my-file.txt
        Assertions.assertEquals("foo", volumeMounts.get(2).getName());
        Assertions.assertEquals("/etc/foodir/my-file.txt", volumeMounts.get(2).getMountPath());
        Assertions.assertEquals("my-file.txt", volumeMounts.get(2).getSubPath());
        Assertions.assertEquals("key-foo", volumes.get(2).getSecret().getItems().get(0).getKey());
        Assertions.assertEquals("my-file.txt", volumes.get(2).getSecret().getItems().get(0).getPath());
        // configmap:bar
        Assertions.assertEquals("bar", volumeMounts.get(3).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/bar", volumeMounts.get(3).getMountPath());
        // configmap:bar/key-bar
        Assertions.assertEquals("bar", volumeMounts.get(4).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/bar/key-bar", volumeMounts.get(4).getMountPath());
        Assertions.assertEquals("key-bar", volumes.get(4).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("key-bar", volumes.get(4).getConfigMap().getItems().get(0).getPath());
        // configmap:bar2/my.properties@/var/dir1/bar.bin
        Assertions.assertEquals("bar2", volumeMounts.get(5).getName());
        Assertions.assertEquals("/var/dir1/bar.bin", volumeMounts.get(5).getMountPath());
        Assertions.assertEquals("bar.bin", volumeMounts.get(5).getSubPath());
        Assertions.assertEquals("my.properties", volumes.get(5).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("bar.bin", volumes.get(5).getConfigMap().getItems().get(0).getPath());
    }

    @Test
    public void shouldAddConfigAndResources() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + RuntimeType.main);
        command.configs = new String[] { "configmap:bar1a/my.key1" };
        command.resources = new String[] { "configmap:bar2/key-bar2", "configmap:bar2a/my.key2@/var/dir2/bar.bin" };
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(RuntimeType.main);
        List<Volume> volumes = deployment.getSpec().getTemplate().getSpec().getVolumes();
        List<VolumeMount> volumeMounts = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        // config configmap:bar1a/my.key1
        Assertions.assertEquals("bar1a", volumeMounts.get(0).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar1a/my.key1", volumeMounts.get(0).getMountPath());
        Assertions.assertEquals("my.key1", volumeMounts.get(0).getSubPath());
        Assertions.assertEquals("my.key1", volumes.get(0).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("my.key1", volumes.get(0).getConfigMap().getItems().get(0).getPath());
        // resources configmap:bar2/key-bar2
        Assertions.assertEquals("bar2", volumeMounts.get(1).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/bar2/key-bar2", volumeMounts.get(1).getMountPath());
        Assertions.assertEquals("key-bar2", volumeMounts.get(1).getSubPath());
        Assertions.assertEquals("key-bar2", volumes.get(1).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("key-bar2", volumes.get(1).getConfigMap().getItems().get(0).getPath());
        // resources configmap:bar2a/my.key2@/var/dir2/bar.bin
        Assertions.assertEquals("bar2a", volumeMounts.get(2).getName());
        Assertions.assertEquals("/var/dir2/bar.bin", volumeMounts.get(2).getMountPath());
        Assertions.assertEquals("bar.bin", volumeMounts.get(2).getSubPath());
        Assertions.assertEquals("my.key2", volumes.get(2).getConfigMap().getItems().get(0).getKey());
        Assertions.assertEquals("bar.bin", volumes.get(2).getConfigMap().getItems().get(0).getPath());
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
        Assertions.assertEquals("openapi",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/openapi/spec.yaml",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("spec.yaml",
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
        Assertions.assertNull(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("demo-app", model.getArtifactId());
        Assertions.assertEquals("1.0", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("quay.io/camel/demo-app:1.0", props.get("jkube.image.name"));
        Assertions.assertEquals("quay.io/camel/demo-app:1.0", props.get("jkube.container-image.name"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddJolokiaSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--trait", "jolokia.enabled=true",
                "--trait", "jolokia.expose=true",
                "--trait", "jolokia.service-port=8779",
                "--trait", "jolokia.service-port-name=jolokia-port",
                "--runtime=" + rt.runtime());
        var exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasService(rt));
        Assertions.assertFalse(hasKnativeService(rt));

        Deployment deployment = getDeployment(rt);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertNull(container.getImage());
        Assertions.assertEquals(2, container.getPorts().size());
        Assertions.assertEquals("jolokia", container.getPorts().get(1).getName());
        Assertions.assertEquals(8778, container.getPorts().get(1).getContainerPort());

        Model model = readMavenModel();
        Assertions.assertEquals("org.example.project", model.getGroupId());
        Assertions.assertEquals("route-service", model.getArtifactId());
        Assertions.assertEquals("1.0-SNAPSHOT", model.getVersion());

        Properties props = model.getProperties();
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.image.name"));
        Assertions.assertEquals("route-service:1.0-SNAPSHOT", props.get("jkube.container-image.name"));

        Service service = getService(rt);
        List<ServicePort> ports = service.getSpec().getPorts();
        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(2, ports.size());
        Assertions.assertEquals("http", ports.get(0).getName());
        Assertions.assertEquals(80, ports.get(0).getPort());
        Assertions.assertEquals("http", ports.get(0).getTargetPort().getStrVal());
        Assertions.assertEquals("jolokia-port", ports.get(1).getName());
        Assertions.assertEquals(8779, ports.get(1).getPort());
        Assertions.assertEquals("jolokia", ports.get(1).getTargetPort().getStrVal());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldObserveByDefault(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--observe", "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot",
                            "camel-observability-services-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus",
                            "camel-quarkus-observability-services", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void ingressTrait(RuntimeType rt) throws Exception {
        KubernetesExport command
                = createCommand(new String[] { "classpath:route.yaml", "src/test/resources/application.properties", },
                        "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(hasIngress(rt));
    }

}
