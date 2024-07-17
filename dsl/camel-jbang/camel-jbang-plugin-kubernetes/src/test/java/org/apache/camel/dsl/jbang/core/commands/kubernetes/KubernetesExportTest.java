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
import java.util.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class KubernetesExportTest {

    private File workingDir;
    private String[] defaultArgs;

    @BeforeEach
    public void setup() throws IOException {
        workingDir = Files.createTempDirectory("camel-k8s-export").toFile();
        workingDir.deleteOnExit();
        defaultArgs = new String[] { "--dir=" + workingDir, "--quiet" };
    }

    @Test
    public void shouldGenerateQuarkusProject() throws Exception {
        shouldGenerateProject(RuntimeType.quarkus);
    }

    @Test
    public void shouldGenerateSpringBootProject() throws Exception {
        shouldGenerateProject(RuntimeType.springBoot);
    }

    void shouldGenerateProject(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--runtime=" + rt.runtime());
        Assertions.assertEquals(0, command.doCall());

        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    @Test
    public void shouldGenerateQuarkusKubernetesManifest() throws Exception {
        shouldGenerateKubernetesManifest(RuntimeType.quarkus);
    }

    @Test
    public void shouldGenerateSpringBootKubernetesManifest() throws Exception {
        shouldGenerateKubernetesManifest(RuntimeType.springBoot);
    }

    void shouldGenerateKubernetesManifest(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-group=camel-test", "--runtime=" + rt.runtime());
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        DeploymentSpec spec = deployment.getSpec();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, spec.getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("route", spec.getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(3, spec.getSelector().getMatchLabels().size());
        Assertions.assertEquals("route", spec.getSelector().getMatchLabels().get("app.kubernetes.io/name"));
        Assertions.assertEquals("route", spec.getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT",
                spec.getTemplate().getSpec().getContainers().get(0).getImage());
    }

    @Test
    public void shouldAddQuarkusContainerSpec() throws Exception {
        shouldAddContainerSpec(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootContainerSpec() throws Exception {
        shouldAddContainerSpec(RuntimeType.springBoot);
    }

    void shouldAddContainerSpec(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--gav=camel-test:route:1.0.0", "--runtime=" + rt.runtime());
        command.traits = new String[] { "container.port=8088", "container.image-pull-policy=IfNotPresent" };
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel-test/route:1.0.0",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals("IfNotPresent",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().size());
        Assertions.assertEquals("http",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getName());
        Assertions.assertEquals(8088,
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort());
    }

    @Test
    public void shouldAddQuarkusVolumes() throws Exception {
        shouldAddVolumes(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootVolumes() throws Exception {
        shouldAddVolumes(RuntimeType.springBoot);
    }

    void shouldAddVolumes(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.volumes = new String[] { "pvc-foo:/container/path/foo", "pvc-bar:/container/path/bar" };
        Assertions.assertEquals(0, command.doCall());

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

    @Test
    public void shouldAddQuarkusEnvVars() throws Exception {
        shouldAddEnvVars(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootEnvVars() throws Exception {
        shouldAddEnvVars(RuntimeType.springBoot);
    }

    void shouldAddEnvVars(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.envVars = new String[] { "CAMEL_FOO=bar", "MY_ENV=foo" };
        Assertions.assertEquals(0, command.doCall());

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

    @Test
    public void shouldAddQuarkusAnnotations() throws Exception {
        shouldAddAnnotations(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootAnnotations() throws Exception {
        shouldAddAnnotations(RuntimeType.springBoot);
    }

    void shouldAddAnnotations(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.annotations = new String[] { "foo=bar" };
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getMetadata().getAnnotations().size());
        Assertions.assertEquals("bar", deployment.getMetadata().getAnnotations().get("foo"));
    }

    @Test
    public void shouldAddQuarkusLabels() throws Exception {
        shouldAddLabels(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootLabels() throws Exception {
        shouldAddLabels(RuntimeType.springBoot);
    }

    void shouldAddLabels(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" },
                "--label=foo=bar", "--runtime=" + rt.runtime());
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        ObjectMeta metadata = deployment.getMetadata();
        Assertions.assertEquals("route", metadata.getName());
        Assertions.assertEquals("route", metadata.getLabels().get("camel.apache.org/integration"));
        Assertions.assertEquals("bar", metadata.getLabels().get("foo"));
        if (rt == RuntimeType.quarkus) {
            Assertions.assertEquals(2, metadata.getLabels().size());
        }
        if (rt == RuntimeType.springBoot) {
            Assertions.assertEquals(4, metadata.getLabels().size());
            Assertions.assertEquals("route", metadata.getLabels().get("app.kubernetes.io/name"));
        }
    }

    @Test
    public void shouldAddQuarkusConfigs() throws Exception {
        shouldAddConfigs(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootConfigs() throws Exception {
        shouldAddConfigs(RuntimeType.springBoot);
    }

    void shouldAddConfigs(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.configs = new String[] { "secret:foo", "configmap:bar" };
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        DeploymentSpec spec = deployment.getSpec();
        Container container = spec.getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, spec.getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(2, container.getVolumeMounts().size());
        Assertions.assertEquals("foo", container.getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_secrets/foo", container.getVolumeMounts().get(0).getMountPath());
        Assertions.assertTrue(container.getVolumeMounts().get(0).getReadOnly());
        Assertions.assertEquals("bar", container.getVolumeMounts().get(1).getName());
        Assertions.assertEquals("/etc/camel/conf.d/_configmaps/bar", container.getVolumeMounts().get(1).getMountPath());
        Assertions.assertTrue(container.getVolumeMounts().get(1).getReadOnly());
    }

    @Test
    public void shouldAddQuarkusResources() throws Exception {
        shouldAddResources(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootResources() throws Exception {
        shouldAddResources(RuntimeType.springBoot);
    }

    void shouldAddResources(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.resources = new String[] { "configmap:foo/file.txt" };
        Assertions.assertEquals(0, command.doCall());

        Deployment deployment = getDeployment(workingDir);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(1, container.getVolumeMounts().size());
        Assertions.assertEquals("file", container.getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/foo/file.txt",
                container.getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("/file.txt", container.getVolumeMounts().get(0).getSubPath());
    }

    @Test
    public void shouldAddQuarkusOpenApis() throws Exception {
        shouldAddOpenApis(RuntimeType.quarkus);
    }

    @Test
    public void shouldAddSpringBootOpenApis() throws Exception {
        shouldAddOpenApis(RuntimeType.springBoot);
    }

    void shouldAddOpenApis(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.openApis = new String[] { "configmap:openapi/spec.yaml" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals(1, container.getVolumeMounts().size());
        Assertions.assertEquals("spec", container.getVolumeMounts().get(0).getName());
        Assertions.assertEquals("/etc/camel/resources.d/_configmaps/openapi/spec.yaml",
                container.getVolumeMounts().get(0).getMountPath());
        Assertions.assertEquals("/spec.yaml", container.getVolumeMounts().get(0).getSubPath());
    }

    @Test
    public void shouldUseQuarkusImage() throws Exception {
        shouldUseImage(RuntimeType.quarkus);
    }

    @Test
    public void shouldUseSpringBootImage() throws Exception {
        shouldUseImage(RuntimeType.springBoot);
    }

    public void shouldUseImage(RuntimeType rt) throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, "--runtime=" + rt.runtime());
        command.image = "quay.io/camel/demo-app:1.0";
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assertions.assertEquals("demo-app", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel/demo-app:1.0", container.getImage());
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
        try (FileInputStream fis = new FileInputStream(new File(workingDir, "src/main/kubernetes/kubernetes.yml"))) {
            return KubernetesHelper.yaml().loadAs(fis, Deployment.class);
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
