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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.MetadataHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.util.StringHelper;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.traits.AddonsBuilder;
import org.apache.camel.v1.integrationspec.traits.Builder;
import org.apache.camel.v1.integrationspec.traits.Camel;
import org.apache.camel.v1.integrationspec.traits.Container;
import org.apache.camel.v1.integrationspec.traits.Environment;
import org.apache.camel.v1.integrationspec.traits.Mount;
import org.apache.camel.v1.integrationspec.traits.Openapi;
import org.apache.camel.v1.integrationspec.traits.ServiceBinding;

/**
 * Utility class manages trait expressions and its conversion to proper trait model.
 */
public final class TraitHelper {

    private TraitHelper() {
        //prevent instantiation of utility class.
    }

    /**
     * Parses given list of trait expressions to proper trait model object. Supports trait options in the form of
     * key=value.
     *
     * @param  traits trait key-value-pairs.
     * @return
     */
    public static Traits parseTraits(String[] traits) {
        if (traits == null || traits.length == 0) {
            return new Traits();
        }

        Map<String, Map<String, Object>> traitConfigMap = new HashMap<>();

        for (String traitExpression : traits) {
            //traitName.key=value
            final String[] trait = traitExpression.split("\\.", 2);
            final String[] traitConfig = trait[1].split("=", 2);

            // the CRD api is in CamelCase, then we have to
            // convert the kebab-case to CamelCase
            final String traitKey = StringHelper.dashToCamelCase(traitConfig[0]);
            final Object traitValue = resolveTraitValue(traitKey, traitConfig[1].trim());
            if (traitConfigMap.containsKey(trait[0])) {
                Map<String, Object> config = traitConfigMap.get(trait[0]);

                if (config.containsKey(traitKey)) {
                    Object existingValue = config.get(traitKey);

                    if (existingValue instanceof List) {
                        List<String> values = (List<String>) existingValue;
                        if (traitValue instanceof List) {
                            List<String> traitValueList = (List<String>) traitValue;
                            values.addAll(traitValueList);
                        } else {
                            values.add(traitValue.toString());
                        }
                    } else if (existingValue instanceof Map) {
                        Map<String, String> values = (Map<String, String>) existingValue;
                        if (traitValue instanceof Map) {
                            Map<String, String> traitValueList = (Map<String, String>) traitValue;
                            values.putAll(traitValueList);
                        } else {
                            final String[] traitValueConfig = traitValue.toString().split("=", 2);
                            values.put(traitValueConfig[0], traitValueConfig[1]);
                        }
                    } else if (traitValue instanceof List) {
                        List<String> traitValueList = (List<String>) traitValue;
                        traitValueList.add(0, existingValue.toString());
                        config.put(traitKey, traitValueList);
                    } else if (traitValue instanceof Map) {
                        Map<String, String> traitValueMap = (Map<String, String>) traitValue;
                        final String[] existingValueConfig = existingValue.toString().split("=", 2);
                        traitValueMap.put(existingValueConfig[0], existingValueConfig[1]);
                        config.put(traitKey, traitValueMap);
                    } else {
                        if (traitKey.endsWith("annotations")) {
                            Map<String, String> map = new LinkedHashMap<>();
                            final String[] traitValueConfig = traitValue.toString().split("=", 2);
                            final String[] existingValueConfig = existingValue.toString().split("=", 2);
                            map.put(traitValueConfig[0], traitValueConfig[1]);
                            map.put(existingValueConfig[0], existingValueConfig[1]);
                            config.put(traitKey, map);
                        } else {
                            config.put(traitKey, Arrays.asList(existingValue.toString(), traitValue));
                        }
                    }
                } else {
                    config.put(traitKey, traitValue);
                }
            } else {
                Map<String, Object> config = new HashMap<>();
                config.put(traitKey, traitValue);
                traitConfigMap.put(trait[0], config);
            }
        }

        Traits traitModel = KubernetesHelper.json().convertValue(traitConfigMap, Traits.class);

        // Handle leftover traits as addons
        Set<?> knownTraits = KubernetesHelper.json().convertValue(traitModel, Map.class).keySet();
        if (knownTraits.size() < traitConfigMap.size()) {
            traitModel.setAddons(new HashMap<>());
            for (Map.Entry<String, Map<String, Object>> traitConfig : traitConfigMap.entrySet()) {
                if (!knownTraits.contains(traitConfig.getKey())) {
                    traitModel.getAddons().put(traitConfig.getKey(),
                            new AddonsBuilder().addToAdditionalProperties(traitConfig.getValue()).build());
                }
            }
        }

        return traitModel;
    }

    /**
     * Resolve trait value with automatic type conversion. Some trait keys (like enabled, verbose) need to be converted
     * to boolean type.
     *
     */
    private static Object resolveTraitValue(String traitKey, String value) {
        if (traitKey.equalsIgnoreCase("enabled") ||
                traitKey.equalsIgnoreCase("verbose")) {
            return Boolean.valueOf(value);
        }

        if (value.startsWith("[") && value.endsWith("]")) {
            String valueArrayExpression = value.substring(1, value.length() - 1);
            List<String> values = new ArrayList<>();
            if (valueArrayExpression.contains(",")) {
                values.addAll(List.of(valueArrayExpression.split(",")));
            } else {
                values.add(valueArrayExpression);
            }
            return values;
        }

        if (value.contains(",")) {
            List<String> values = new ArrayList<>();
            for (String entry : value.split(",")) {
                values.add(resolveTraitValue("", entry).toString());
            }
            return values;
        }

        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public static void configureConnects(Traits traitsSpec, String[] connects) {
        if (connects == null || connects.length == 0) {
            return;
        }
        ServiceBinding serviceBindingTrait = Optional.ofNullable(traitsSpec.getServiceBinding()).orElseGet(ServiceBinding::new);
        if (serviceBindingTrait.getServices() == null) {
            serviceBindingTrait.setServices(new ArrayList<>());
        }
        serviceBindingTrait.getServices().addAll(List.of(connects));
        traitsSpec.setServiceBinding(serviceBindingTrait);
    }

    public static void configureEnvVars(Traits traitsSpec, String[] envVars) {
        if (envVars == null || envVars.length == 0) {
            return;
        }
        Environment environmentTrait = Optional.ofNullable(traitsSpec.getEnvironment()).orElseGet(Environment::new);
        if (environmentTrait.getVars() == null) {
            environmentTrait.setVars(new ArrayList<>());
        }
        environmentTrait.getVars().addAll(List.of(envVars));
        traitsSpec.setEnvironment(environmentTrait);
    }

    public static void configureBuildProperties(Traits traitsSpec, String[] buildProperties) {
        if (buildProperties == null || buildProperties.length == 0) {
            return;
        }

        Builder builderTrait = Optional.ofNullable(traitsSpec.getBuilder()).orElseGet(Builder::new);
        if (builderTrait.getProperties() == null) {
            builderTrait.setProperties(new ArrayList<>());
        }
        builderTrait.getProperties().addAll(List.of(buildProperties));
        traitsSpec.setBuilder(builderTrait);
    }

    public static void configureProperties(Traits traitsSpec, String[] properties) {
        if (properties == null || properties.length == 0) {
            return;
        }

        Camel camelTrait = Optional.ofNullable(traitsSpec.getCamel()).orElseGet(Camel::new);
        if (camelTrait.getProperties() == null) {
            camelTrait.setProperties(new ArrayList<>());
        }
        camelTrait.getProperties().addAll(List.of(properties));
        traitsSpec.setCamel(camelTrait);
    }

    public static void configureOpenApiSpec(Traits traitsSpec, String openApi) {
        if (openApi == null || !openApi.startsWith("configmap:")) {
            return;
        }

        Openapi openapiTrait = Optional.ofNullable(traitsSpec.getOpenapi()).orElseGet(Openapi::new);
        if (openapiTrait.getConfigmaps() == null) {
            openapiTrait.setConfigmaps(new ArrayList<>());
        }
        openapiTrait.getConfigmaps().add(openApi);
        traitsSpec.setOpenapi(openapiTrait);
    }

    public static void configureMountTrait(Traits traitsSpec, String[] configs, String[] resources, String[] volumes) {
        if (configs == null && resources == null && volumes == null) {
            return;
        }

        Mount mountTrait = Optional.ofNullable(traitsSpec.getMount()).orElseGet(Mount::new);

        if (configs != null && configs.length > 0) {
            if (mountTrait.getConfigs() == null) {
                mountTrait.setConfigs(new ArrayList<>());
            }
            mountTrait.getConfigs().addAll(List.of(configs));
        }

        if (resources != null && resources.length > 0) {
            if (mountTrait.getResources() == null) {
                mountTrait.setResources(new ArrayList<>());
            }
            mountTrait.getResources().addAll(List.of(resources));
        }

        if (volumes != null && volumes.length > 0) {
            if (mountTrait.getVolumes() == null) {
                mountTrait.setVolumes(new ArrayList<>());
            }
            mountTrait.getVolumes().addAll(List.of(volumes));
        }

        traitsSpec.setMount(mountTrait);
    }

    public static void configureContainerImage(
            Traits traitsSpec, String image, String imageRegistry, String imageGroup, String imageName, String version) {
        Container containerTrait = Optional.ofNullable(traitsSpec.getContainer()).orElseGet(Container::new);
        if (image != null) {
            containerTrait.setImage(image);
            traitsSpec.setContainer(containerTrait);
        } else if (containerTrait.getImage() == null) {
            String registryPrefix = "";
            if ("minikube".equals(imageRegistry) || "minikube-registry".equals(imageRegistry)) {
                registryPrefix = "localhost:5000/";
            } else if ("kind".equals(imageRegistry) || "kind-registry".equals(imageRegistry)) {
                registryPrefix = "localhost:5001/";
            } else if (imageRegistry != null && !imageRegistry.isEmpty()) {
                registryPrefix = imageRegistry + "/";
            }

            imageGroup = Optional.ofNullable(imageGroup).orElse("");
            if (!imageGroup.isEmpty()) {
                containerTrait.setImage("%s%s/%s:%s".formatted(registryPrefix, imageGroup, imageName, version));
            } else {
                containerTrait.setImage("%s%s:%s".formatted(registryPrefix, imageName, version));
            }

            // Plain export command always exposes a health endpoint on 8080.
            // Skip this, when we decide that the health endpoint can be disabled.
            if (containerTrait.getPort() == null) {
                containerTrait.setPortName(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME);
                containerTrait.setPort((long) ContainerTrait.DEFAULT_CONTAINER_PORT);
            }

            traitsSpec.setContainer(containerTrait);
        }
    }

    /**
     * Inspect sources in context to retrieve routes that expose a Http service as an endpoint.
     *
     * @param  context the trait context holding all route sources.
     * @return         true when routes expose a Http service, false otherwise.
     */
    public static boolean exposesHttpService(TraitContext context) {
        try {
            boolean exposesHttpServices = false;
            CamelCatalog catalog = context.getCatalog();
            if (context.getSources() != null) {
                for (Source source : context.getSources()) {
                    SourceMetadata metadata = context.inspectMetaData(source);
                    if (MetadataHelper.exposesHttpServices(catalog, metadata)) {
                        exposesHttpServices = true;
                        break;
                    }
                }
            }

            return exposesHttpServices;
        } catch (Exception e) {
            context.printer().printf("Failed to apply service trait %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Extract properties traits (camel.jbang.trait.key=value) and transform them into regular trait form (key=value)
     *
     * @param  properties properties
     * @return            traits
     */
    public static String[] extractTraitsFromProperties(Properties properties) {
        if (properties != null && !properties.isEmpty()) {
            Stream<String> propertyTraits = properties.entrySet().stream()
                    .filter(property -> property.getKey().toString().startsWith("camel.jbang.trait"))
                    .map(property -> StringHelper.after(property.getKey().toString(), "camel.jbang.trait.") + "="
                                     + properties.get(property.getKey()).toString());
            return propertyTraits.collect(Collectors.toSet()).toArray(String[]::new);
        }
        return new String[0];
    }

    /**
     * Extract annotation traits (trait.camel.apache.org/key=value) and transform them into regular trait form
     * (key=value)
     *
     * @param  annotations annotations
     * @return             traits
     */
    public static String[] extractTraitsFromAnnotations(String[] annotations) {
        if (annotations != null && annotations.length > 0) {
            Stream<String> annotationTraits = Stream.of(annotations)
                    .filter(annotation -> annotation.startsWith("trait.camel.apache.org/"))
                    .map(annotation -> StringHelper.after(annotation, "trait.camel.apache.org/"));
            return annotationTraits.collect(Collectors.toSet()).toArray(String[]::new);
        }
        return new String[0];
    }

    /**
     * Merge all the traits from multiple sources in one keeping overrides priority by its position in the list. A trait
     * property value in the array 0 will have priority on the value of the same trait property in an array 1. Supports
     * trait options in the form of key=value.
     *
     * @param  traitsBySource traits grouped by source
     * @return                the traits merged
     */
    public static String[] mergeTraits(String[]... traitsBySource) {
        if (traitsBySource == null || traitsBySource.length == 0) {
            return new String[0];
        }
        Set<String> existingKeys = new HashSet<>();
        List<String> mergedTraits = new ArrayList<>();
        for (String[] traits : traitsBySource) {
            if (traits != null && traits.length > 0) {
                for (String trait : traits) {
                    final String[] traitConfig = trait.split("=", 2);
                    if (!existingKeys.contains(traitConfig[0])) {
                        mergedTraits.add(trait);
                    }
                }
                existingKeys.clear();
                for (String trait : mergedTraits) {
                    final String[] traitConfig = trait.split("=", 2);
                    existingKeys.add(traitConfig[0]);
                }
            }
        }
        return mergedTraits.toArray(new String[0]);
    }
}
