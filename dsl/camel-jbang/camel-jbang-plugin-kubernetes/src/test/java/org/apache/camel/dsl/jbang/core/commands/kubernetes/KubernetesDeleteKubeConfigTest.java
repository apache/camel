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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * camel kubernetes delete --kube-config acts on the cluster of that file: before, it used the current context
 * (CAMEL-26934), so a delete aimed at staging acted on the cluster of KUBECONFIG.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
class KubernetesDeleteKubeConfigTest extends KubernetesBaseTestSupport {

    @TempDir
    Path dir;

    @Test
    public void deleteUsesTheClusterOfTheKubeConfig() throws Exception {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName("routes")
                .withNamespace("staging")
                .withLabels(Map.of(BaseTrait.KUBERNETES_LABEL_NAME, "routes"))
                .endMetadata()
                .build();
        kubernetesClient.apps().deployments().inNamespace("staging").resource(deployment).create();
        Path kubeConfig = dir.resolve("staging.yaml");
        Files.writeString(kubeConfig, """
                apiVersion: v1
                kind: Config
                clusters:
                - name: staging
                  cluster:
                    server: %s
                    insecure-skip-tls-verify: true
                contexts:
                - name: staging
                  context:
                    cluster: staging
                    user: staging
                    namespace: staging
                current-context: staging
                users:
                - name: staging
                  user:
                    token: secret
                """.formatted(kubernetesClient.getMasterUrl()));

        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        // the current context is another cluster, which cannot be reached
        command.withClient(new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder().withMasterUrl("http://localhost:1").withRequestTimeout(500)
                        .withConnectionTimeout(500).build())
                .build());
        command.kubeConfig = kubeConfig.toString();
        command.name = "routes";

        int exit = command.doCall();

        Assertions.assertEquals(0, exit, printer.getOutput());
        Assertions.assertTrue(printer.getOutput().contains("Deleting resources in namespace 'staging' with name: routes"),
                printer.getOutput());
        Assertions.assertTrue(printer.getOutput().contains("Deployment 'routes'"), printer.getOutput());
        Assertions.assertNull(kubernetesClient.apps().deployments().inNamespace("staging").withName("routes").get());
    }
}
