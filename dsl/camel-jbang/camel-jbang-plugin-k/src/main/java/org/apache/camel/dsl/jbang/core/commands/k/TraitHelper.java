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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.util.StringHelper;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.traits.AddonsBuilder;
import org.apache.camel.v1.integrationspec.traits.Builder;
import org.apache.camel.v1.integrationspec.traits.Camel;
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
     * Parses given list of trait expressions to proper trait model object.
     *
     * @param  traits
     * @return
     */
    public static Traits parseTraits(String[] traits) {
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
                    } else if (traitValue instanceof List) {
                        List<String> traitValueList = (List<String>) traitValue;
                        traitValueList.add(0, existingValue.toString());
                        config.put(traitKey, traitValueList);
                    } else {
                        config.put(traitKey, Arrays.asList(existingValue.toString(), traitValue));
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
     * @param  traitKey
     * @param  value
     * @return
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

    public static void configureOpenApiSpec(Traits traitsSpec, String[] openApis) {
        if (openApis == null || openApis.length == 0) {
            return;
        }

        Openapi openapiTrait = Optional.ofNullable(traitsSpec.getOpenapi()).orElseGet(Openapi::new);
        if (openapiTrait.getConfigmaps() == null) {
            openapiTrait.setConfigmaps(new ArrayList<>());
        }
        openapiTrait.getConfigmaps().addAll(List.of(openApis));
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
}
