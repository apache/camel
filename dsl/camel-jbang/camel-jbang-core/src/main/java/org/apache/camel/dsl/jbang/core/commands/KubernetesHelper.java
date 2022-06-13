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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.api.model.BinaryBuildSource;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RoutePortBuilder;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
@Deprecated
public final class KubernetesHelper {

    private KubernetesHelper() {
    }

    public static BuildConfig createBuildConfig(
            String namespace, String name, String version, String filename, String sourceImage) {

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withAnnotations(Map.of("jarFileName", filename))
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        return new BuildConfigBuilder()
                .withMetadata(metadata.build())
                .withNewSpec()
                .withNewSource().withType("Binary").withBinary(new BinaryBuildSource(filename)).endSource()
                .withNewOutput()
                .withNewTo().withKind("ImageStreamTag").withName(name + ":" + version).endTo()
                .endOutput()
                .withNewStrategy().withType("Source")
                .withNewSourceStrategy().withNewFrom().withKind("ImageStreamTag").withNamespace("openshift")
                .withName(sourceImage).endFrom()
                .endSourceStrategy()
                .endStrategy()
                .withNewSource().withType("Binary")
                .endSource()
                .endSpec()
                .build();
    }

    public static ImageStream createImageStream(String namespace, String name, String version) {

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        return new ImageStreamBuilder()
                .withMetadata(metadata.build())
                .withNewSpec()
                .withNewLookupPolicy(false)
                .endSpec()
                .build();
    }

    public static Route createRoute(String namespace, String name, String version, int targetPort) {

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        return new RouteBuilder()
                .withMetadata(metadata.build())
                .withNewSpec()
                .withPort(new RoutePortBuilder().withNewTargetPort(targetPort).build())
                .withNewTo().withKind("Service").withName(name)
                .endTo()
                .endSpec()
                .build();
    }

    public static Service createService(
            String namespace, String name, String version, int port, int targetPort, boolean minikube, int nodePort) {

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        ServicePortBuilder servicePort = new ServicePortBuilder()
                .withName("http")
                .withPort(port)
                .withNewTargetPort(targetPort);
        if (minikube) {
            servicePort.withNodePort(nodePort);
        }

        ServiceSpecBuilder spec = new ServiceSpecBuilder()
                .withSelector(getSelector(name, version))
                .withPorts(servicePort.build());
        if (minikube) {
            spec.withType("NodePort");
        }

        return new ServiceBuilder()
                .withMetadata(metadata.build())
                .withSpec(spec.build())
                .build();
    }

    public static Deployment createDeployment(
            String namespace, String name, String image, String version, int containerPort, int replica) {

        if (image == null) {
            image = namespace + "/" + name + ":" + version;
        }

        EnvVar envVar = new EnvVarBuilder()
                .withName("KUBERNETES_NAMESPACE")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.namespace")
                .endFieldRef()
                .endValueFrom()
                .build();

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        return new DeploymentBuilder()
                .withMetadata(metadata.build())
                .withNewSpec()
                .withReplicas(replica)
                .withNewSelector()
                .addToMatchLabels(getMatchLabels(name))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(getLabels(name, version))
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(image)
                .withImagePullPolicy("IfNotPresent")
                .addNewPort()
                .withContainerPort(containerPort)
                .withName("http")
                .withProtocol("TCP")
                .endPort()
                .addNewEnv()
                .withName("KUBERNETES_NAMESPACE")
                .withValueFrom(envVar.getValueFrom())
                .endEnv()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    public static Map<String, String> getLabels(String name, String version) {
        return Map.of(
                "app", name,
                "app.kubernetes.io/name", name,
                "app.kubernetes.io/component", name,
                "app.kubernetes.io/instance", name,
                "app.kubernetes.io/version", version,
                "app.kubernetes.io/part-of", name,
                "app.openshift.io/runtime", "camel",
                "app.kubernetes.io/runtime", "camel");
    }

    public static Map<String, String> getMatchLabels(String name) {
        return Map.of(
                "app", name);
    }

    public static Map<String, String> getSelector(String name, String version) {
        return Map.of(
                "app.kubernetes.io/name", name,
                "app.kubernetes.io/version", version);
    }

    public static OpenShiftConfig getOpenShiftConfig(String server, String username, String password, String token) {
        if (token != null) {
            return new OpenShiftConfigBuilder().withMasterUrl(server).withOauthToken(token).withTrustCerts(true).build();
        } else {
            return new OpenShiftConfigBuilder().withMasterUrl(server).withUsername(username).withPassword(password)
                    .withTrustCerts(true).build();
        }
    }

    public static Config getConfig(String server, String username, String password, String token) {
        if (token != null && server != null) {
            return new ConfigBuilder().withMasterUrl(server).withOauthToken(token).withTrustCerts(true).build();
        } else if (username != null && token != null && server != null) {
            return new ConfigBuilder().withMasterUrl(server).withUsername(username).withPassword(password).withTrustCerts(true)
                    .build();
        } else {
            return new ConfigBuilder().build();
        }
    }
}
