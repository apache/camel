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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.vertx.VertxHttpClientFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
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
            printClientInfo(kubernetesClient);
        }

        return kubernetesClient;
    }

    /**
     * Create or get Kubernetes client with given config.
     */
    public static KubernetesClient getKubernetesClient(String config) {
        if (clients.containsKey(config)) {
            return clients.get(config);
        }

        var client = new KubernetesClientBuilder().withConfig(config).build();
        printClientInfo(client);
        return clients.put(config, client);
    }

    private static void printClientInfo(KubernetesClient client) {
        var printer = CommandHelper.GetPrinter();
        if (printer != null) {
            var serverUrl = client.getConfiguration().getMasterUrl();
            var info = client.getKubernetesVersion();
            printer.println(String.format("Kubernetes v%s.%s %s", info.getMajor(), info.getMinor(), serverUrl));
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

    // KubernetesClientVertx can no longer be used in ShutdownHook
    // https://issues.apache.org/jira/browse/CAMEL-21621
    public static KubernetesClient createKubernetesClientForShutdownHook() {
        System.setProperty("vertx.disableDnsResolver", "true");
        var vertx = Vertx.vertx((new VertxOptions())
                .setFileSystemOptions((new FileSystemOptions())
                        .setFileCachingEnabled(false)
                        .setClassPathResolvingEnabled(false))
                .setUseDaemonThread(true));
        var client = new KubernetesClientBuilder()
                .withHttpClientFactory(new VertxHttpClientFactory(vertx))
                .build();
        return client;
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
        ClusterType cs = ClusterType
                .valueOf(Optional.ofNullable(clusterType).map(String::toUpperCase).orElse(ClusterType.KUBERNETES.name()));
        String manifestFile = switch (cs) {
            case KIND, MINIKUBE -> "kubernetes";
            case OPENSHIFT -> "_openshift";
            default -> cs.name().toLowerCase();
        };
        return new File(workingDir, "%s.%s".formatted(manifestFile, extension));
    }
}
