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

package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.Collection;
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
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

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
        Representer representer = new Representer(new DumperOptions()) {
            @Override
            protected NodeTuple representJavaBeanProperty(
                    Object javaBean, Property property, Object propertyValue, Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null || (propertyValue instanceof Collection && ((Collection<?>) propertyValue).isEmpty())
                        ||
                        (propertyValue instanceof Map && ((Map<?, ?>) propertyValue).isEmpty())) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        representer.getPropertyUtils().setSkipMissingProperties(true);
        return new Yaml(representer);
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
    static void setKubernetesClient(KubernetesClient kubernetesClient) {
        KubernetesHelper.kubernetesClient = kubernetesClient;
    }
}
