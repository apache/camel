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
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class KubernetesExportTest extends KubernetesBaseTest {

    private File workingDir;
    private String[] defaultArgs;

    @BeforeEach
    public void setup() {
        super.setup();

        try {
            workingDir = Files.createTempDirectory("camel-k8s-export").toFile();
            workingDir.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }

        defaultArgs = new String[] { "--dir=" + workingDir, "--quiet" };
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot));
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
        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("route", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(1, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("route",
                deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());

        Assertions.assertFalse(hasService(workingDir));
        Assertions.assertFalse(hasKnativeService(workingDir));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddServiceSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        command.doCall();

        Assertions.assertTrue(hasService(workingDir));
        Assertions.assertFalse(hasKnativeService(workingDir));

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route-service", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("camel-test/route-service:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().size());
        Assertions.assertEquals("http",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getName());
        Assertions.assertEquals(8080,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort());

        Service service = getService(workingDir);
        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(1, service.getSpec().getPorts().size());
        Assertions.assertEquals("http", service.getSpec().getPorts().get(0).getName());
        Assertions.assertEquals(80, service.getSpec().getPorts().get(0).getPort());
        Assertions.assertEquals("http", service.getSpec().getPorts().get(0).getTargetPort().getStrVal());
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
        command.doCall();

        Assertions.assertTrue(hasService(workingDir));

        Deployment deployment = getDeployment(workingDir);
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

        Service service = getService(workingDir);
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

        Assertions.assertFalse(hasService(workingDir));
        Assertions.assertTrue(hasKnativeService(workingDir));

        io.fabric8.knative.serving.v1.Service service = getResource(workingDir, io.fabric8.knative.serving.v1.Service.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing Knative service in Kubernetes manifest"));

        Assertions.assertEquals("route-service", service.getMetadata().getName());
        Assertions.assertEquals(3, service.getMetadata().getLabels().size());
        Assertions.assertEquals("route-service", service.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("true", service.getMetadata().getLabels().get("bindings.knative.dev/include"));
        Assertions.assertEquals("cluster-local", service.getMetadata().getLabels().get("networking.knative.dev/visibility"));
        Assertions.assertEquals(1, service.getMetadata().getAnnotations().size());
        Assertions.assertEquals("60", service.getMetadata().getAnnotations().get("serving.knative.dev/rolloutDuration"));
        Assertions.assertEquals(1, service.getSpec().getTemplate().getMetadata().getLabels().size());
        Assertions.assertEquals("route-service",
                service.getSpec().getTemplate().getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
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
    public void shouldAddVolumes(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.volumes = new String[] { "pvc-foo:/container/path/foo", "pvc-bar:/container/path/bar" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
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
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
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
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getMetadata().getAnnotations().size());
        Assertions.assertEquals("bar", deployment.getMetadata().getAnnotations().get("foo"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddLabels(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--label=foo=bar", "--runtime=" + rt.runtime());
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(2, deployment.getMetadata().getLabels().size());
        Assertions.assertEquals("route", deployment.getMetadata().getLabels().get("camel.apache.org/integration"));
        Assertions.assertEquals("bar", deployment.getMetadata().getLabels().get("foo"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldAddConfigs(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.configs = new String[] { "secret:foo", "configmap:bar" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
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
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
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
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.openApis = new String[] { "configmap:openapi/spec.yaml" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
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
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("demo-app", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel/demo-app:1.0",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    private KubernetesExport createCommand(String[] files, String... args) {
        var argsArr = Optional.ofNullable(args).orElse(new String[0]);
        var argsLst = new ArrayList<>(Arrays.asList(argsArr));
        argsLst.addAll(Arrays.asList(defaultArgs));
        KubernetesExport command = new KubernetesExport(new CamelJBangMain(), files);
        CommandLine.populateCommand(command, argsLst.toArray(new String[0]));
        return command;
    }

    private Deployment getDeployment(File workingDir) throws IOException {
        return getResource(workingDir, Deployment.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));
    }

    private Service getService(File workingDir) throws IOException {
        return getResource(workingDir, Service.class)
                .orElseThrow(() -> new RuntimeCamelException("Missing service in Kubernetes manifest"));
    }

    private boolean hasService(File workingDir) throws IOException {
        return getResource(workingDir, Service.class).isPresent();
    }

    private boolean hasKnativeService(File workingDir) throws IOException {
        return getResource(workingDir, io.fabric8.knative.serving.v1.Service.class).isPresent();
    }

    private <T extends HasMetadata> Optional<T> getResource(File workingDir, Class<T> type) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(workingDir, "src/main/kubernetes/kubernetes.yml"))) {
            List<HasMetadata> resources = kubernetesClient.load(fis).items();
            return resources.stream()
                    .filter(it -> type.isAssignableFrom(it.getClass()))
                    .map(type::cast)
                    .findFirst();
        }
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
