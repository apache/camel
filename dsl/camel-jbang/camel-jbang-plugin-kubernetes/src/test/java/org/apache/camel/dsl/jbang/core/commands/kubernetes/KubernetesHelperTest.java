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
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class KubernetesHelperTest {

    private final Path workingDir = Path.of("target", "kubernetes");

    // KubernetesExport builds with the kubernetes-maven-plugin for every cluster type but OpenShift,
    // and that plugin writes the manifest as kubernetes.yml (kubernetes.json)
    @ParameterizedTest
    @EnumSource(value = ClusterType.class, names = "OPENSHIFT", mode = EnumSource.Mode.EXCLUDE)
    void manifestOfKubernetesMavenPlugin(ClusterType clusterType) {
        String name = clusterType.name().toLowerCase(Locale.ROOT);

        Assertions.assertEquals(workingDir.resolve("kubernetes.yml"),
                KubernetesHelper.getKubernetesManifestPath(name, workingDir));
        Assertions.assertEquals(workingDir.resolve("kubernetes.json"),
                KubernetesHelper.getKubernetesManifestPath(clusterType.name(), workingDir, "json"));
        Assertions.assertEquals(new File(workingDir.toFile(), "kubernetes.yml"),
                KubernetesHelper.getKubernetesManifest(name, workingDir.toFile()));
    }

    @Test
    void manifestOfOpenShiftMavenPlugin() {
        Assertions.assertEquals(workingDir.resolve("openshift.yml"),
                KubernetesHelper.getKubernetesManifestPath("openshift", workingDir));
        Assertions.assertEquals(workingDir.resolve("openshift.json"),
                KubernetesHelper.getKubernetesManifestPath("OPENSHIFT", workingDir, "json"));
        Assertions.assertEquals(new File(workingDir.toFile(), "openshift.yml"),
                KubernetesHelper.getKubernetesManifest("openshift", workingDir.toFile()));
    }

    @Test
    void manifestOfKnativeService() {
        // KubernetesRun reads the service.yml that the knative-service trait writes
        Path jkubeDir = Path.of("src", "main", "jkube");
        Assertions.assertEquals(jkubeDir.resolve("service.yml"),
                KubernetesHelper.getKubernetesManifestPath("service", jkubeDir));
    }

    @Test
    void manifestWithoutClusterType() {
        Assertions.assertEquals(workingDir.resolve("kubernetes.yml"),
                KubernetesHelper.getKubernetesManifestPath(null, workingDir));
    }
}
