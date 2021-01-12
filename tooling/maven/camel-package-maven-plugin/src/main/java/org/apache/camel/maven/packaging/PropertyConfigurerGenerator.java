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

import java.io.IOException;
import java.io.Writer;
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

    public static void generatePropertyConfigurer(
            String pn, String cn, String en,
            String pfqn, String psn, boolean hasSuper, boolean component, boolean extended, boolean bootstrap,
            Collection<? extends BaseOptionModel> options, ComponentModel model, Writer w)
            throws IOException {

        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import java.util.Map;\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;\n");
        w.write("import org.apache.camel.spi.PropertyConfigurerGetter;\n");
        w.write("import org.apache.camel.spi.ConfigurerStrategy;\n");
        w.write("import org.apache.camel.spi.GeneratedPropertyConfigurer;\n");
        w.write("import org.apache.camel.util.CaseInsensitiveMap;\n");
        w.write("import " + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " extends " + psn
                + " implements GeneratedPropertyConfigurer");
        if (extended) {
            w.write(", ExtendedPropertyConfigurerGetter");
        } else {
            w.write(", PropertyConfigurerGetter");
        }
        w.write(" {\n");
        w.write("\n");

        // sort options A..Z so they always have same order
        if (!options.isEmpty()) {
            options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());
        }

        // if from component model then we can not optimize this and use a static block
        if (extended) {
            if (model != null || !hasSuper) {
                // static block for all options which is immutable information
                w.write("    private static final Map<String, Object> ALL_OPTIONS;\n");
                if (model != null) {
                    w.write(generateAllOptions(cn, bootstrap, component, model));
                } else {
                    w.write(generateAllOptions(cn, bootstrap, options));
                }
                w.write("\n");
            }
        }

        if (!options.isEmpty() || !hasSuper) {
            if (component) {
                // if its a component configurer then configuration classes are optional and we need
                // to generate a method that can lazy create a new configuration if it was null
                for (BaseOptionModel bo : findConfigurations(options)) {
                    w.write(createGetOrCreateConfiguration(en, bo.getConfigurationClass(),
                            bo.getConfigurationField()));
                    w.write("\n");
                }
            }

            w.write("    @Override\n");
            w.write("    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        " + en + " target = (" + en + ") obj;\n");
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String setterLambda = setterLambda(getOrSet, option.getJavaType(), option.getSetterMethod(),
                            option.getConfigurationField(), component, option.getType());
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.write(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.write(String.format("        case \"%s\": %s; return true;\n", option.getName(), setterLambda));
                }
                if (hasSuper) {
                    w.write("        default: return super.configure(camelContext, obj, name, value, ignoreCase);\n");
                } else {
                    w.write("        default: return false;\n");
                }
                w.write("        }\n");
            }
            w.write("    }\n");

            if (extended) {
                // generate method that returns all the options
                w.write("\n");
                w.write("    @Override\n");
                w.write("    public Map<String, Object> getAllOptions(Object target) {\n");
                if (model != null || !hasSuper) {
                    w.write("        return ALL_OPTIONS;\n");
                    w.write("    }\n");
                } else {
                    w.write("        Map<String, Object> answer = super.getAllOptions(target);\n");
                    if (!options.isEmpty()) {
                        for (BaseOptionModel option : options) {
                            // type may contain generics so remove those
                            String type = option.getJavaType();
                            if (type.indexOf('<') != -1) {
                                type = type.substring(0, type.indexOf('<'));
                            }
                            type = type.replace('$', '.');
                            w.write(String.format("        answer.put(\"%s\", %s.class);\n", option.getName(), type));
                        }
                        w.write("        return answer;\n");
                        w.write("    }\n");
                    }
                }
            }
            if (bootstrap && extended) {
                w.write("\n");
                w.write("    public static void clearBootstrapConfigurers() {\n");
                w.write("        ALL_OPTIONS.clear();\n");
                w.write("    }\n");
            }

            // generate method for autowired
            if (options.stream().anyMatch(BaseOptionModel::isAutowired)) {
                w.write("\n");
                w.write("    @Override\n");
                w.write("    public String[] getAutowiredNames() {\n");
                String names = options.stream()
                        .filter(BaseOptionModel::isAutowired)
                        .map(BaseOptionModel::getName)
                        .map(PropertyConfigurerGenerator::quote)
                        .collect(Collectors.joining(","));
                w.write("        return new String[]{");
                w.write(names);
                w.write("};\n");
                w.write("    }\n");
            }

            // generate method for getting a property type
            w.write("\n");
            w.write("    @Override\n");
            w.write("    public Class<?> getOptionType(String name, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    // type may contain generics so remove those
                    String type = option.getJavaType();
                    if (type.indexOf('<') != -1) {
                        type = type.substring(0, type.indexOf('<'));
                    }
                    type = type.replace('$', '.');
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.write(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.write(String.format("        case \"%s\": return %s.class;\n", option.getName(), type));
                }
                if (hasSuper) {
                    w.write("        default: return super.getOptionType(name, ignoreCase);\n");
                } else {
                    w.write("        default: return null;\n");
                }
                w.write("        }\n");
            }
            w.write("    }\n");

            // generate method for getting a property
            w.write("\n");
            w.write("    @Override\n");
            w.write("    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        " + en + " target = (" + en + ") obj;\n");
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String getterLambda = getterLambda(getOrSet, option.getJavaType(), option.getGetterMethod(),
                            option.getConfigurationField(), component);
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.write(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.write(String.format("        case \"%s\": return %s;\n", option.getName(), getterLambda));
                }
                if (hasSuper) {
                    w.write("        default: return super.getOptionValue(obj, name, ignoreCase);\n");
                } else {
                    w.write("        default: return null;\n");
                }
                w.write("        }\n");
            }
            w.write("    }\n");

            // nested type was stored in extra as we use BaseOptionModel to hold the option data
            boolean hasNestedTypes
                    = options.stream().map(BaseOptionModel::getNestedType).anyMatch(s -> s != null && !s.trim().isEmpty());
            if (hasNestedTypes) {
                w.write("\n");
                w.write("    @Override\n");
                w.write("    public Object getCollectionValueType(Object target, String name, boolean ignoreCase) {\n");
                if (!options.isEmpty()) {
                    w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                    for (BaseOptionModel option : options) {
                        String nestedType = option.getNestedType();
                        if (nestedType != null && !nestedType.isEmpty()) {
                            nestedType = nestedType.replace('$', '.');
                            if (!option.getName().toLowerCase().equals(option.getName())) {
                                w.write(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                            }
                            w.write(String.format("        case \"%s\": return %s.class;\n", option.getName(), nestedType));
                        }
                    }
                    if (hasSuper) {
                        w.write("        default: return super.getCollectionValueType(target, name, ignoreCase);\n");
                    } else {
                        w.write("        default: return null;\n");
                    }
                    w.write("        }\n");
                }
                w.write("    }\n");
            }
        }

        w.write("}\n");
        w.write("\n");
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
            String optionKind) {
        // type may contain generics so remove those
        if (type.indexOf('<') != -1) {
            type = type.substring(0, type.indexOf('<'));
        }
        type = type.replace('$', '.');
        if (configurationField != null) {
            if (component) {
                String methodName
                        = "getOrCreate" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
                getOrSet = methodName + "(target).set" + getOrSet;
            } else {
                getOrSet = "target.get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1)
                           + "().set" + getOrSet;
            }
        } else {
            getOrSet = "target.set" + getOrSet;
        }

        // target.setGroupSize(property(camelContext, java.lang.Integer.class, value))
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
        String line4 = String.format("        }\n");
        String line5 = String.format("        return target.%s();\n", getter);
        String line6 = String.format("    }\n");

        sb.append(line1).append(line2).append(line3).append(line4).append(line5).append(line6);
        return sb.toString();
    }

    private static String quote(String n) {
        return "\"" + n + "\"";
    }

}
