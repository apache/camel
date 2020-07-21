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
package org.apache.camel.maven.packaging.dsl.component;

import java.util.Collection;
import java.util.Optional;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.commons.lang3.StringUtils;

/**
 * DSL Generator class that generates component implementation of the component builder interface.
 * E.g: class KafkaComponentBuilderImpl extends AbstractComponentBuilder implements KafkaComponentBuilder
 */
public final class ComponentDslInnerImplBuilderGenerator {
    private static final String BUILDER_IMPL_SUFFIX = "Impl";


    private final JavaClass javaClass;
    private final ComponentModel componentModel;
    private final String classBuilderName;

    private ComponentDslInnerImplBuilderGenerator(final JavaClass javaClass, final ComponentModel componentModel, final String interfaceBuilderName) {
        this.javaClass = javaClass;
        this.componentModel = componentModel;
        this.classBuilderName = interfaceBuilderName;
        // generate class
        generateJavaClass();
    }

    public static ComponentDslInnerImplBuilderGenerator generateClass(final JavaClass javaClass, final ComponentModel componentModel, final String classBuilderName) {
        return new ComponentDslInnerImplBuilderGenerator(javaClass, componentModel, classBuilderName);
    }

    public String getGeneratedClassName() {
        return classBuilderName + BUILDER_IMPL_SUFFIX;
    }

    private void generateJavaClass() {
        setClassNameAndType();
        setMethods();
    }

    private void setClassNameAndType() {
        javaClass.setName(getGeneratedClassName())
                .setPackagePrivate()
                .setStatic(false)
                .extendSuperType(String.format("AbstractComponentBuilder<%s>", componentModel.getShortJavaType()))
                .implementInterface(classBuilderName);
    }

    private void setMethods() {
        javaClass.addMethod()
                .setProtected()
                .setReturnType(componentModel.getShortJavaType())
                .setName("buildConcreteComponent")
                .setBody(String.format("return new %s();", componentModel.getShortJavaType()))
                .addAnnotation(Override.class);

        // then configuration classes are optional and we need
        // to generate a method that can lazy create a new configuration if it was null
        Optional<ComponentModel.ComponentOptionModel> configurationOption = findConfiguration(componentModel.getComponentOptions());
        if (configurationOption.isPresent()) {
            ComponentModel.ComponentOptionModel config = configurationOption.get();
            javaClass.addMethod()
                .setPrivate()
                .setReturnType(config.getConfigurationClass())
                .setName("getOrCreateConfiguration")
                .addParameter(componentModel.getJavaType(), "component")
                .setBody(createGetOrCreateConfigurationBody(config.getConfigurationClass(), config.getConfigurationField()));
        }

        javaClass.addMethod()
                .setProtected()
                .setReturnType("boolean")
                .setName("setPropertyOnComponent")
                .addParameter("Component", "component")
                .addParameter(String.class, "name")
                .addParameter(Object.class, "value")
                .setBody(generatePropertiesSetters(componentModel))
                .addAnnotation(Override.class);
    }

    private String generatePropertiesSetters(final ComponentModel componentModel) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("switch (name) {\n");

        componentModel.getComponentOptions().forEach(componentOptionModel -> {
            String prefix = componentOptionModel.getConfigurationField() != null ? "getOrCreateConfiguration" : "";

            // type may contain generics so remove those
            String type = componentOptionModel.getJavaType();
            if (type.indexOf('<') != -1) {
                type = type.substring(0, type.indexOf('<'));
            }
            type = type.replace('$', '.');

            final String setterAsString = String.format("case \"%s\": %s((%s) component).set%s((%s) value); return true;\n",
                    componentOptionModel.getName(), prefix, componentModel.getShortJavaType(),
                    StringUtils.capitalize(componentOptionModel.getName()), type);
            stringBuilder.append(setterAsString);
        });

        stringBuilder.append("default: return false;\n}");

        return stringBuilder.toString();
    }

    private static Optional<ComponentModel.ComponentOptionModel> findConfiguration(Collection<ComponentModel.ComponentOptionModel> options) {
        return options.stream().filter(o -> o.getConfigurationField() != null).findFirst();
    }

    private static String createGetOrCreateConfigurationBody(String configurationClass, String configurationField) {
        String getter = "get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
        String setter = "set" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);

        StringBuilder sb = new StringBuilder();
        String line1 = String.format("if (component.%s() == null) {\n", getter);
        String line2 = String.format("    component.%s(new %s());\n", setter, configurationClass);
        String line3 = String.format("}\n");
        String line4 = String.format("return component.%s();\n", getter);

        sb.append(line1).append(line2).append(line3).append(line4);
        return sb.toString();
    }

}
