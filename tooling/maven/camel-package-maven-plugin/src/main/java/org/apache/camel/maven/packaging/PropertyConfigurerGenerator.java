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
package org.apache.camel.maven.packaging;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

public final class PropertyConfigurerGenerator {

    private PropertyConfigurerGenerator() {
    }

    public static String generatePropertyConfigurer(
            String pn, String cn, String en,
            String pfqn, String psn, boolean hasSuper, boolean component, boolean extended, boolean bootstrap,
            Collection<? extends BaseOptionModel> options, ComponentModel model) {

        StringBuilder w = new StringBuilder();

        w.append("/* ").append(AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.append("package ").append(pn).append(";\n");
        w.append('\n');
        w.append("import java.util.Map;\n");
        w.append("\n");
        w.append("import org.apache.camel.CamelContext;\n");
        w.append("import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;\n");
        w.append("import org.apache.camel.spi.PropertyConfigurerGetter;\n");
        w.append("import org.apache.camel.spi.ConfigurerStrategy;\n");
        w.append("import org.apache.camel.spi.GeneratedPropertyConfigurer;\n");
        w.append("import org.apache.camel.util.CaseInsensitiveMap;\n");
        w.append("import ").append(pfqn).append(";\n");
        w.append('\n');
        w.append("/**\n");
        w.append(" * ").append(AbstractGeneratorMojo.GENERATED_MSG).append('\n');
        w.append(" */\n");
        w.append("@SuppressWarnings(\"unchecked\")\n");
        w.append("public class ").append(cn).append(" extends ").append(psn).append(" implements GeneratedPropertyConfigurer");
        if (extended) {
            w.append(", ExtendedPropertyConfigurerGetter");
        } else {
            w.append(", PropertyConfigurerGetter");
        }
        w.append(" {\n");
        w.append('\n');

        // sort options A..Z so they always have same order
        if (!options.isEmpty()) {
            options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());
        }

        // if from component model then we can not optimize this and use a static block
        if (extended) {
            if (model != null || !hasSuper) {
                // static block for all options which is immutable information
                w.append("    private static final Map<String, Object> ALL_OPTIONS;\n");
                if (model != null) {
                    w.append(generateAllOptions(cn, bootstrap, component, model));
                } else {
                    w.append(generateAllOptions(cn, bootstrap, options));
                }
                w.append('\n');
            }
        }

        boolean stub = model != null && model.getName().equals("stub");
        if (stub && options.isEmpty() && !component) {
            // special for stub to accept and ignore lenient options
            w.append("    @Override\n");
            w.append(
                    "    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {\n");
            w.append("        super.configure(camelContext, obj, name, value, ignoreCase);\n");
            w.append("        return true;\n");
            w.append("    }\n");
        } else if (!options.isEmpty() || !hasSuper) {
            if (component) {
                // if its a component configurer then configuration classes are optional and we need
                // to generate a method that can lazy create a new configuration if it was null
                for (BaseOptionModel bo : findConfigurations(options)) {
                    w.append(createGetOrCreateConfiguration(en, bo.getConfigurationClass(),
                            bo.getConfigurationField()));
                    w.append('\n');
                }
            }

            w.append("    @Override\n");
            w.append(
                    "    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.append("        ").append(en).append(" target = (").append(en).append(") obj;\n");
                w.append("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    boolean builder = option instanceof AbstractGenerateConfigurerMojo.ConfigurerOption
                            && ((AbstractGenerateConfigurerMojo.ConfigurerOption) option).isBuilderMethod();
                    String setterLambda = setterLambda(getOrSet, option.getJavaType(), option.getSetterMethod(),
                            option.getConfigurationField(), component, option.getType(), builder);
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.append(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.append(String.format("        case \"%s\": %s; return true;\n", option.getName(), setterLambda));
                }
                if (stub) {
                    // special for stub to accept and ignore lenient options
                    w.append("        default: return true;\n");
                } else if (hasSuper) {
                    w.append("        default: return super.configure(camelContext, obj, name, value, ignoreCase);\n");
                } else {
                    w.append("        default: return false;\n");
                }
                w.append("        }\n");
            } else {
                w.append("        return false;\n");
            }
            w.append("    }\n");

            if (extended) {
                // generate method that returns all the options
                w.append('\n');
                w.append("    @Override\n");
                w.append("    public Map<String, Object> getAllOptions(Object target) {\n");
                if (model != null || !hasSuper) {
                    w.append("        return ALL_OPTIONS;\n");
                    w.append("    }\n");
                } else {
                    w.append("        Map<String, Object> answer = super.getAllOptions(target);\n");
                    if (!options.isEmpty()) {
                        for (BaseOptionModel option : options) {
                            // type may contain generics so remove those
                            String type = option.getJavaType();
                            if (type.indexOf('<') != -1) {
                                type = type.substring(0, type.indexOf('<'));
                            }
                            type = type.replace('$', '.');
                            w.append(String.format("        answer.put(\"%s\", %s.class);\n", option.getName(), type));
                        }
                        w.append("        return answer;\n");
                        w.append("    }\n");
                    }
                }
            }
            if (bootstrap && extended) {
                w.append('\n');
                w.append("    public static void clearBootstrapConfigurers() {\n");
                w.append("        ALL_OPTIONS.clear();\n");
                w.append("    }\n");
            }

            // generate method for autowired
            if (options.stream().anyMatch(BaseOptionModel::isAutowired)) {
                w.append('\n');
                w.append("    @Override\n");
                w.append("    public String[] getAutowiredNames() {\n");
                String names = options.stream()
                        .filter(BaseOptionModel::isAutowired)
                        .map(BaseOptionModel::getName)
                        .map(PropertyConfigurerGenerator::quote)
                        .collect(Collectors.joining(","));
                w.append("        return new String[]{");
                w.append(names);
                w.append("};\n");
                w.append("    }\n");
            }

            // generate method for getting a property type
            w.append('\n');
            w.append("    @Override\n");
            w.append("    public Class<?> getOptionType(String name, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.append("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    // type may contain generics so remove those
                    String type = option.getJavaType();
                    if (type.indexOf('<') != -1) {
                        type = type.substring(0, type.indexOf('<'));
                    }
                    type = type.replace('$', '.');
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.append(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.append(String.format("        case \"%s\": return %s.class;\n", option.getName(), type));
                }
                if (hasSuper) {
                    w.append("        default: return super.getOptionType(name, ignoreCase);\n");
                } else {
                    w.append("        default: return null;\n");
                }
                w.append("        }\n");
            } else {
                w.append("        return null;\n");
            }
            w.append("    }\n");

            // generate method for getting a property
            w.append('\n');
            w.append("    @Override\n");
            w.append("    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.append("        ").append(en).append(" target = (").append(en).append(") obj;\n");
                w.append("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String getterLambda = getterLambda(getOrSet, option.getJavaType(), option.getGetterMethod(),
                            option.getConfigurationField(), component);
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.append(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.append(String.format("        case \"%s\": return %s;\n", option.getName(), getterLambda));
                }
                if (hasSuper) {
                    w.append("        default: return super.getOptionValue(obj, name, ignoreCase);\n");
                } else {
                    w.append("        default: return null;\n");
                }
                w.append("        }\n");
            } else {
                w.append("        return null;\n");

            }
            w.append("    }\n");

            // nested type was stored in extra as we use BaseOptionModel to hold the option data
            boolean hasNestedTypes
                    = options.stream().map(BaseOptionModel::getNestedType).anyMatch(s -> s != null && !s.isBlank());
            if (hasNestedTypes) {
                w.append('\n');
                w.append("    @Override\n");
                w.append("    public Object getCollectionValueType(Object target, String name, boolean ignoreCase) {\n");
                if (!options.isEmpty()) {
                    w.append("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                    for (BaseOptionModel option : options) {
                        String nestedType = option.getNestedType();
                        if (nestedType != null && !nestedType.isEmpty()) {
                            nestedType = nestedType.replace('$', '.');
                            if (!option.getName().toLowerCase().equals(option.getName())) {
                                w.append(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                            }
                            w.append(String.format("        case \"%s\": return %s.class;\n", option.getName(), nestedType));
                        }
                    }
                    if (hasSuper) {
                        w.append("        default: return super.getCollectionValueType(target, name, ignoreCase);\n");
                    } else {
                        w.append("        default: return null;\n");
                    }
                    w.append("        }\n");
                } else {
                    w.append("        return null;\n");
                }
                w.append("    }\n");
            }
        }

        w.append("}\n");
        w.append('\n');

        return w.toString();
    }

    private static String generateAllOptions(String className, boolean bootstrap, boolean component, ComponentModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("    static {\n");
        sb.append("        Map<String, Object> map = new CaseInsensitiveMap();\n");
        if (component) {
            for (ComponentModel.ComponentOptionModel option : model.getComponentOptions()) {
                // type may contain generics so remove those
                String type = option.getJavaType();
                if (type.indexOf('<') != -1) {
                    type = type.substring(0, type.indexOf('<'));
                }
                type = type.replace('$', '.');
                sb.append(String.format("        map.put(\"%s\", %s.class);\n", option.getName(), type));
            }
        } else {
            for (ComponentModel.EndpointOptionModel option : model.getEndpointOptions()) {
                // type may contain generics so remove those
                String type = option.getJavaType();
                if (type.indexOf('<') != -1) {
                    type = type.substring(0, type.indexOf('<'));
                }
                type = type.replace('$', '.');
                sb.append(String.format("        map.put(\"%s\", %s.class);\n", option.getName(), type));
            }
        }
        sb.append("        ALL_OPTIONS = map;\n");
        if (bootstrap) {
            sb.append("        ConfigurerStrategy.addBootstrapConfigurerClearer(").append(className)
                    .append("::clearBootstrapConfigurers);\n");
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private static String generateAllOptions(
            String className, boolean bootstrap, Collection<? extends BaseOptionModel> options) {
        StringBuilder sb = new StringBuilder();
        sb.append("    static {\n");
        sb.append("        Map<String, Object> map = new CaseInsensitiveMap();\n");
        for (BaseOptionModel option : options) {
            // type may contain generics so remove those
            String type = option.getJavaType();
            if (type.indexOf('<') != -1) {
                type = type.substring(0, type.indexOf('<'));
            }
            type = type.replace('$', '.');
            sb.append(String.format("        map.put(\"%s\", %s.class);\n", option.getName(), type));
        }
        sb.append("        ALL_OPTIONS = map;\n");
        if (bootstrap) {
            // for non API configurers we can clear the map after bootstrap
            sb.append("        ConfigurerStrategy.addBootstrapConfigurerClearer(").append(className)
                    .append("::clearBootstrapConfigurers);\n");
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private static Set<BaseOptionModel> findConfigurations(Collection<? extends BaseOptionModel> options) {
        final Set<String> found = new LinkedHashSet<>();
        final Set<BaseOptionModel> answer = new LinkedHashSet<>();
        for (BaseOptionModel bo : options.stream().filter(o -> o.getConfigurationField() != null)
                .collect(Collectors.toList())) {
            if (!found.contains(bo.getConfigurationClass())) {
                found.add(bo.getConfigurationClass());
                answer.add(bo);
            }
        }
        return answer;
    }

    private static String setterLambda(
            String getOrSet, String type, String setterMethod, String configurationField, boolean component,
            String optionKind, boolean builder) {
        // type may contain generics so remove those
        if (type.indexOf('<') != -1) {
            type = type.substring(0, type.indexOf('<'));
        }
        type = type.replace('$', '.');
        String prefix = builder ? "with" : "set";
        if (configurationField != null) {
            if (component) {
                String methodName
                        = "getOrCreate" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
                getOrSet = methodName + "(target)." + prefix + getOrSet;
            } else {
                getOrSet = "target.get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1)
                           + "()." + prefix + getOrSet;
            }
        } else {
            getOrSet = "target." + prefix + getOrSet;
        }

        String rv;
        if ("duration".equals(optionKind) && "long".equals(type)) {
            rv = "property(camelContext, java.time.Duration.class, value).toMillis()";
        } else {
            rv = String.format("property(camelContext, %s.class, value)", type);
        }
        String v = setterMethod != null ? String.format(setterMethod, rv) : rv;
        return String.format("%s(%s)", getOrSet, v);
    }

    private static String getterLambda(
            String getOrSet, String type, String getterMethod, String configurationField, boolean component) {
        String prefix;
        if (getterMethod == null || getterMethod.isEmpty()) {
            prefix = "boolean".equals(type) ? "is" : "get";
        } else {
            prefix = "";
            getOrSet = getterMethod;
        }
        if (configurationField != null) {
            if (component) {
                String methodName
                        = "getOrCreate" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
                getOrSet = methodName + "(target)." + prefix + getOrSet;
            } else {
                getOrSet = "target.get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1)
                           + "()." + prefix + getOrSet;
            }
        } else {
            getOrSet = "target." + prefix + getOrSet;
        }

        return getOrSet + "()";
    }

    private static String createGetOrCreateConfiguration(
            String targetClass, String configurationClass, String configurationField) {
        String getter = "get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
        String setter = "set" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
        String methodName
                = "getOrCreate" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);

        StringBuilder sb = new StringBuilder();
        String line1 = String.format("    private %s %s(%s target) {\n", configurationClass, methodName, targetClass);
        String line2 = String.format("        if (target.%s() == null) {\n", getter);
        String line3 = String.format("            target.%s(new %s());\n", setter, configurationClass);
        String line4 = "        }\n";
        String line5 = String.format("        return target.%s();\n", getter);
        String line6 = "    }\n";

        sb.append(line1).append(line2).append(line3).append(line4).append(line5).append(line6);
        return sb.toString();
    }

    private static String quote(String n) {
        return "\"" + n + "\"";
    }

}
