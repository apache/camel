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
import java.io.IOException;
import java.nio.file.Files;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesExportTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws IOException {
        workingDir = Files.createTempDirectory("camel-k8s-export").toFile();
        workingDir.deleteOnExit();
    }

    @Test
    public void shouldGenerateKubernetesManifest() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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
    }

    @Test
    public void shouldAddContainerSpec() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
        command.traits = new String[] { "container.port=8088", "container.image-pull-policy=IfNotPresent" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel-test/route:1.0-SNAPSHOT",
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
    public void shouldAddVolumes() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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

    @Test
    public void shouldAddEnvVars() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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

    @Test
    public void shouldAddAnnotations() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
        command.annotations = new String[] { "foo=bar" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getMetadata().getAnnotations().size());
        Assertions.assertEquals("bar", deployment.getMetadata().getAnnotations().get("foo"));
    }

    @Test
    public void shouldAddLabels() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
        command.labels = new String[] { "foo=bar" };
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(2, deployment.getMetadata().getLabels().size());
        Assertions.assertEquals("bar", deployment.getMetadata().getLabels().get("foo"));
    }

    @Test
    public void shouldAddConfigs() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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

    @Test
    public void shouldAddResources() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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

    @Test
    public void shouldAddOpenApis() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
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

    @Test
    public void shouldUseImage() throws Exception {
        KubernetesExport command = createCommand(new String[] { "classpath:route.yaml" }, workingDir.toString());
        command.image = "quay.io/camel/demo-app:1.0";
        command.doCall();

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("demo-app", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("quay.io/camel/demo-app:1.0",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    private KubernetesExport createCommand(String[] files, String exportDir) {
        KubernetesExport command = new KubernetesExport(
                new CamelJBangMain(),
                RuntimeType.quarkus, files, exportDir, true);
        command.imageGroup = "camel-test";
        return command;
    }

    private Deployment getDeployment(File workingDir) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(workingDir, "src/main/kubernetes/kubernetes.yml"))) {
            return KubernetesHelper.yaml().loadAs(fis, Deployment.class);
        }
    }
}
