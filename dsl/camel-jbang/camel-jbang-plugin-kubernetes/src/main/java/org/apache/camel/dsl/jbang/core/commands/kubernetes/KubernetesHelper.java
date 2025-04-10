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
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.apache.camel.dsl.jbang.core.common.YamlHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.yaml.snakeyaml.Yaml;

/**
 * Helper class provides access to cached Kubernetes client. Also provides access to generic Json and Yaml mappers.
 */
public final class KubernetesHelper {

    private static KubernetesClient kubernetesClient;

    /** Clients with custom config */
    private static final Map<String, KubernetesClient> clients = new HashMap<>();

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build()
                .setDefaultPropertyInclusion(
                        JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY));
    }

    private KubernetesHelper() {
        //prevent instantiation of utility class.
    }

    /**
     * Gets the default Kubernetes client.
     */
    public static KubernetesClient getKubernetesClient() {
        if (kubernetesClient == null) {
            kubernetesClient = new KubernetesClientBuilder().build();
        }
        setKubernetesClientProperties();
        return kubernetesClient;
    }

    /**
     * Create or get Kubernetes client with given config.
     */
    public static KubernetesClient getKubernetesClient(String config) {
        if (clients.containsKey(config)) {
            return clients.get(config);
        }
        setKubernetesClientProperties();
        var client = new KubernetesClientBuilder().withConfig(config).build();
        return clients.put(config, client);
    }

    // set short timeouts to fail fast in case it's not connected to a cluster and don't waste time
    // the user can override these values by setting the property in the cli
    private static void setKubernetesClientProperties() {
        if (System.getProperty("kubernetes.connection.timeout") == null) {
            System.setProperty("kubernetes.connection.timeout", "2000");
        }
        if (System.getProperty("kubernetes.request.timeout") == null) {
            System.setProperty("kubernetes.request.timeout", "2000");
        }
        if (System.getProperty("kubernetes.request.retry.backoffLimit") == null) {
            System.setProperty("kubernetes.request.retry.backoffLimit", "1");
        }
    }

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream.
     */
    public static Yaml yaml() {
        return YamlHelper.yaml();
    }

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream. Uses the given class loader as base constructor. This is mandatory when
     * additional classes have been downloaded via Maven for instance when loading a Camel JBang plugin.
     */
    public static Yaml yaml(ClassLoader classLoader) {
        return YamlHelper.yaml(classLoader);
    }

    public static ObjectMapper json() {
        return OBJECT_MAPPER;
    }

    /**
     * Verify what cluster this shell console is connected to, currently it tests for Openshift and Minikube, in case of
     * errors or no connected cluster, it defaults to Kubernetes.
     *
     * @return The cluster type may be: Openshift, Minikube or Kubernetes.
     */
    static ClusterType discoverClusterType() {
        ClusterType cluster = ClusterType.KUBERNETES;
        if (isConnectedToOpenshift()) {
            cluster = ClusterType.OPENSHIFT;
        } else if (isConnectedToMinikube()) {
            cluster = ClusterType.MINIKUBE;
        }
        return cluster;
    }

    private static boolean isConnectedToOpenshift() {
        boolean ocp = false;
        try {
            APIGroup apiGroup = getKubernetesClient().getApiGroup("config.openshift.io");
            ocp = apiGroup != null;
        } catch (RuntimeException e) {
            System.out.println("Failed to detect cluster: " + e.getMessage() + ", default to kubernetes.");
        }
        return ocp;
    }

    private static boolean isConnectedToMinikube() {
        boolean minikube = false;
        boolean minikubeEnv = false;
        try {
            ResourceDefinitionContext nodecrd = new ResourceDefinitionContext.Builder()
                    .withVersion("v1")
                    .withKind("Node")
                    .withNamespaced(false)
                    .build();
            // if there is a node with minikube label, then it's minikube
            GenericKubernetesResourceList list = getKubernetesClient().genericKubernetesResources(nodecrd)
                    .withLabels(Collections.singletonMap("minikube.k8s.io/name", null)).list();
            minikube = list.getItems().size() > 0;
            // thse env properties are set when running eval $(minikube docker-env) in the console
            // this is important for the docker builder to actually build the image in the exposed docker from the minikube registry
            minikubeEnv = System.getenv("MINIKUBE_ACTIVE_DOCKERD") != null
                    && System.getenv("DOCKER_TLS_VERIFY") != null;
            if (minikube && !minikubeEnv) {
                System.out.println(
                        "It seems you have minikube running but forgot to run \"eval $(minikube docker-env)\", default cluster to kubernetes.");
            }
        } catch (Exception e) {
            // ignore it, since we try to discover the cluster and don't want the caller to handle any error
        }
        return minikube && minikubeEnv;
    }

    // when minikube is used with the registry addon exposed
    // the build of images uses docker builder directly from the registry inside minikube
    // that doesn't generate the container image digest in the registry
    // that poses a problem when deploying a knative-service, since it validates the container image to have a digest
    // then it fails with: failed to resolve image to digest
    // so, for development purposes we disable this validation in minikube
    // https://knative.dev/docs/serving/configuration/deployment/#skipping-tag-resolution
    public static void skipKnativeImageTagResolutionInMinikube() {
        ConfigMap cm = getKubernetesClient().configMaps().inNamespace("knative-serving").withName("config-deployment").get();
        Map<String, String> data = cm.getData();
        String skipTag = data.get("registries-skipping-tag-resolving");
        if (skipTag == null || !skipTag.contains("localhost:5000")) {
            // patch the cm/config-deployment in knative-serving namespace with
            // registries-skipping-tag-resolving: localhost:5000
            getKubernetesClient().configMaps().inNamespace("knative-serving").withName("config-deployment").edit(
                    c -> new ConfigMapBuilder(c).addToData("registries-skipping-tag-resolving", "localhost:5000").build());
        }
    }

    /**
     * Sanitize given name to meet Kubernetes resource naming requirements.
     *
     * @param  name to sanitize.
     * @return      sanitized name ready to be used as a Kubernetes resource name.
     */
    public static String sanitize(String name) {
        if (name != null) {
            name = FileUtil.onlyName(name);
            name = StringHelper.sanitize(name);
            name = StringHelper.camelCaseToDash(name);
            name = name.toLowerCase(Locale.US);
            name = name.replaceAll("[^a-z0-9-]", "");
            name = name.trim();
        }
        return name;
    }

    /**
     * Overwrites the kubernetes client. Typically used by unit tests.
     */
    public static void setKubernetesClient(KubernetesClient kubernetesClient) {
        KubernetesHelper.kubernetesClient = kubernetesClient;
    }

    /**
     * Dump given domain model object as YAML. Uses Json conversion to generic map as intermediate step. This makes sure
     * to properly write Json additional properties.
     */
    public static String dumpYaml(Object model) {
        return yaml().dumpAsMap(json().convertValue(model, Map.class));
    }

    public static Map<String, Object> toJsonMap(Object model) {
        return json().convertValue(model, Map.class);
    }

    public static File resolveKubernetesManifest(String clusterType, String workingDir) throws FileNotFoundException {
        return resolveKubernetesManifest(clusterType, new File(workingDir));
    }

    public static File resolveKubernetesManifest(String clusterType, String workingDir, String extension)
            throws FileNotFoundException {
        return resolveKubernetesManifest(clusterType, new File(workingDir), extension);
    }

    public static File resolveKubernetesManifest(String clusterType, File workingDir) throws FileNotFoundException {
        return resolveKubernetesManifest(clusterType, workingDir, "yml");
    }

    public static File resolveKubernetesManifest(String clusterType, File workingDir, String extension)
            throws FileNotFoundException {

        var manifest = getKubernetesManifest(clusterType, workingDir);
        if (manifest.exists()) {
            return manifest;
        }

        throw new FileNotFoundException(
                "Unable to resolve Kubernetes manifest file type `%s` in folder: %s"
                        .formatted(extension, workingDir.toPath().toString()));
    }

    public static String getPodPhase(Pod pod) {
        return Optional.ofNullable(pod).map(p -> p.getStatus().getPhase()).orElse("Unknown");
    }

    public static File getKubernetesManifest(String clusterType, String workingDir) {
        return getKubernetesManifest(clusterType, new File(workingDir));
    }

    public static File getKubernetesManifest(String clusterType, File workingDir) {
        return getKubernetesManifest(clusterType, workingDir, "yml");
    }

    public static File getKubernetesManifest(String clusterType, File workingDir, String extension) {
        String manifestFile;
        if (ClusterType.KIND.isEqualTo(clusterType) || ClusterType.MINIKUBE.isEqualTo(clusterType)) {
            manifestFile = "kubernetes";
        } else {
            manifestFile = Optional.ofNullable(clusterType).map(String::toLowerCase).orElse("kubernetes");
        }
        return new File(workingDir, "%s.%s".formatted(manifestFile, extension));
    }
}
