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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
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
                .build()
                .setDefaultPropertyInclusion(
                        JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY));
    }

    private KubernetesHelper() {
        //prevent instantiation of utility class.
    }

    /**
     * Gets the default Kubernetes client.
     *
     * @return
     */
    public static KubernetesClient getKubernetesClient() {
        if (kubernetesClient == null) {
            kubernetesClient = new KubernetesClientBuilder().build();
        }

        return kubernetesClient;
    }

    /**
     * Create or get Kubernetes client with given config.
     *
     * @param  config
     * @return
     */
    public static KubernetesClient getKubernetesClient(String config) {
        if (clients.containsKey(config)) {
            return clients.get(config);
        }

        return clients.put(config, new KubernetesClientBuilder().withConfig(config).build());
    }

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream.
     *
     * @return
     */
    public static Yaml yaml() {
        return YamlHelper.yaml();
    }

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream. Uses the given class loader as base constructor. This is mandatory when
     * additional classes have been downloaded via Maven for instance when loading a Camel JBang plugin.
     *
     * @return
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
        name = FileUtil.onlyName(name);
        name = StringHelper.sanitize(name);
        name = StringHelper.camelCaseToDash(name);
        name = name.toLowerCase(Locale.US);
        name = name.replaceAll("[^a-z0-9-]", "");
        name = name.trim();
        return name;
    }

    /**
     * Overwrites the kubernetes client. Typically used by unit tests.
     *
     * @param kubernetesClient
     */
    public static void setKubernetesClient(KubernetesClient kubernetesClient) {
        KubernetesHelper.kubernetesClient = kubernetesClient;
    }

    /**
     * Dump given domain model object as YAML. Uses Json conversion to generic map as intermediate step. This makes sure
     * to properly write Json additional properties.
     *
     * @param  model
     * @return
     */
    public static String dumpYaml(Object model) {
        return yaml().dumpAsMap(json().convertValue(model, Map.class));
    }
}
