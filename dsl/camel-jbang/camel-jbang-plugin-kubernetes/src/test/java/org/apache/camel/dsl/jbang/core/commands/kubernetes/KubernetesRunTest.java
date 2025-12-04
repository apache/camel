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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
@EnabledIf("isDockerAvailable")
class KubernetesRunTest extends KubernetesBaseTest {

    private StringPrinter printer;

    @BeforeEach
    public void setup() {
        // Set Camel version with system property value, usually set via Maven surefire plugin
        // In case you run this test via local Java IDE you need to provide the system property or a default value here
        VersionHelper.setCamelVersion(System.getProperty("camel.version", ""));
        printer = new StringPrinter();
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.main),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.quarkus));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldHandleMissingSourceFile(RuntimeType rt) throws Exception {
        KubernetesRun command = createCommand(
                List.of("mickey-mouse.groovy"),
                "--disable-auto=true",
                "--output=yaml",
                "--runtime=" + rt.runtime(),
                "--java-version=17");
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);

        Assertions.assertTrue(printer.getOutput().contains("ERROR: Project export failed!"));
    }

    @Test
    public void verifyProperties() throws Exception {
        KubernetesRun command = createCommand(
                List.of("classpath:route.yaml"),
                "--gav=examples:route:1.0.0",
                "--runtime=quarkus",
                "--name=my-route-props",
                "--disable-auto=true",
                "--image-registry=quay.io",
                "--image-group=camel-test",
                "--output=yaml",
                "--property=a=b",
                "--property=c=d",
                "--property=src/test/resources/my-route-props1.properties",
                "--property=file:src/test/resources/my-route-props2.properties",
                "--java-version=17");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Properties materializedProps = new Properties();
        String propsFilepath = ".camel-jbang-run/my-route-props/src/main/resources/application.properties";
        try (FileInputStream input = new FileInputStream(new File(propsFilepath))) {
            materializedProps.load(input);
        }

        Assertions.assertEquals("b", materializedProps.get("a"));
        Assertions.assertEquals("d", materializedProps.get("c"));
        Assertions.assertEquals("v1", materializedProps.get("k1"));
        Assertions.assertEquals("v2", materializedProps.get("k2"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateKubernetesManifest(RuntimeType rt) throws Exception {
        KubernetesRun command = createCommand(
                List.of("classpath:route.yaml"),
                "--disable-auto=true",
                "--image-registry=quay.io",
                "--image-group=camel-test",
                "--output=yaml",
                "--trait",
                "container.image-pull-policy=IfNotPresent",
                "--runtime=" + rt.runtime(),
                "--java-version=17");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        var manifest = getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = kubernetesClient.load(manifest).items();
        Assertions.assertEquals(2, resources.size());

        Deployment deployment = resources.stream()
                .filter(it -> Deployment.class.isAssignableFrom(it.getClass()))
                .map(Deployment.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));

        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        var labels = deployment.getMetadata().getLabels();
        var matchLabels = deployment.getSpec().getSelector().getMatchLabels();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, containers.size());
        Assertions.assertEquals("route", containers.get(0).getName());
        Assertions.assertEquals("route", labels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("route", matchLabels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals(
                "quay.io/camel-test/route:1.0-SNAPSHOT", containers.get(0).getImage());
        Assertions.assertEquals("IfNotPresent", containers.get(0).getImagePullPolicy());

        // verify the container health probes path to /observe accordingly to the camel-observability-services
        if (RuntimeType.quarkus == RuntimeType.fromValue(rt.runtime())) {
            Assertions.assertEquals(
                    "/observe/health/live",
                    containers.get(0).getLivenessProbe().getHttpGet().getPath());
            Assertions.assertEquals(
                    "/observe/health/ready",
                    containers.get(0).getReadinessProbe().getHttpGet().getPath());
            Assertions.assertEquals(
                    "/observe/health/started",
                    containers.get(0).getStartupProbe().getHttpGet().getPath());
            Assertions.assertEquals(
                    9876,
                    containers.get(0).getReadinessProbe().getHttpGet().getPort().getIntVal());
            Assertions.assertEquals(
                    9876,
                    containers.get(0).getLivenessProbe().getHttpGet().getPort().getIntVal());
            Assertions.assertEquals(
                    9876,
                    containers.get(0).getStartupProbe().getHttpGet().getPort().getIntVal());
        } else if (RuntimeType.springBoot == RuntimeType.fromValue(rt.runtime())) {
            // spring-boot doesn't set the startup probe
            Assertions.assertEquals(
                    "/observe/health/liveness",
                    containers.get(0).getLivenessProbe().getHttpGet().getPath());
            Assertions.assertEquals(
                    "/observe/health/readiness",
                    containers.get(0).getReadinessProbe().getHttpGet().getPath());
            Assertions.assertEquals(
                    9876,
                    containers.get(0).getReadinessProbe().getHttpGet().getPort().getIntVal());
            Assertions.assertEquals(
                    9876,
                    containers.get(0).getLivenessProbe().getHttpGet().getPort().getIntVal());
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateKubernetesCronjobManifest(RuntimeType rt) throws Exception {
        KubernetesRun command = createCommand(
                List.of("classpath:route.yaml"),
                "--disable-auto=true",
                "--image-registry=quay.io",
                "--image-group=camel-test",
                "--output=yaml",
                "--service-account=my-svc-account",
                "--runtime=" + rt.runtime(),
                "--java-version=17");
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

        var manifest = getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = kubernetesClient.load(manifest).items();
        Assertions.assertEquals(2, resources.size());

        CronJob cronjob = resources.stream()
                .filter(it -> CronJob.class.isAssignableFrom(it.getClass()))
                .map(CronJob.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing cronjob in Kubernetes manifest"));

        JobSpec jobSpec = cronjob.getSpec().getJobTemplate().getSpec();
        Assertions.assertEquals("route", cronjob.getMetadata().getName());
        Assertions.assertEquals("0 22 * * 1-5", cronjob.getSpec().getSchedule());
        Assertions.assertEquals("Europe/Lisbon", cronjob.getSpec().getTimeZone());
        Assertions.assertEquals(2, cronjob.getSpec().getStartingDeadlineSeconds());
        Assertions.assertEquals(3, jobSpec.getActiveDeadlineSeconds());
        Assertions.assertEquals(4, jobSpec.getBackoffLimit());
        Assertions.assertEquals(
                "Never", jobSpec.getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
        Assertions.assertEquals(
                "my-svc-account", jobSpec.getTemplate().getSpec().getServiceAccountName());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldHandleUnsupportedOutputFormat(RuntimeType rt) throws Exception {
        KubernetesRun command = createCommand(
                List.of("classpath:route.yaml"),
                "--disable-auto=true",
                "--output=wrong",
                "--runtime=" + rt.runtime(),
                "--java-version=17");

        Assertions.assertEquals(1, command.doCall());
        Assertions.assertTrue(
                printer.getOutput().endsWith("ERROR: Unsupported output format 'wrong' (supported: yaml, json)"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateKubernetesNamespace(RuntimeType rt) throws Exception {
        KubernetesRun command = createCommand(
                List.of("classpath:route.yaml"),
                "--disable-auto=true",
                "--image-registry=quay.io",
                "--image-group=camel-test",
                "--output=yaml",
                "--namespace",
                "custom",
                "--runtime=" + rt.runtime(),
                "--java-version=17");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        var manifest = getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = kubernetesClient.load(manifest).items();
        Assertions.assertEquals(2, resources.size());

        Deployment deployment = resources.stream()
                .filter(it -> Deployment.class.isAssignableFrom(it.getClass()))
                .map(Deployment.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));

        var labels = deployment.getMetadata().getLabels();
        var matchLabels = deployment.getSpec().getSelector().getMatchLabels();
        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals("custom", deployment.getMetadata().getNamespace());
        Assertions.assertEquals(1, containers.size());
        Assertions.assertEquals("route", containers.get(0).getName());
        Assertions.assertEquals("route", labels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("route", matchLabels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals(
                "quay.io/camel-test/route:1.0-SNAPSHOT", containers.get(0).getImage());
    }

    private KubernetesRun createCommand(List<String> files, String... args) {
        var argsArr = Optional.ofNullable(args).orElse(new String[0]);
        var argsLst = new ArrayList<>(Arrays.asList(argsArr));
        var jbangMain = new CamelJBangMain().withPrinter(printer);
        KubernetesRun command = new KubernetesRun(jbangMain, files);
        CommandLine.populateCommand(command, argsLst.toArray(new String[0]));
        command.imageBuild = false;
        command.imagePush = false;
        return command;
    }
}
