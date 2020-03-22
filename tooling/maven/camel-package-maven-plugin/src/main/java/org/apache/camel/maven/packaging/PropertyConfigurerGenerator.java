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
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.BaseOptionModel;

public final class PropertyConfigurerGenerator {

    private PropertyConfigurerGenerator() {
    }

    public static void generatePropertyConfigurer(String pn, String cn, String en,
                                                  String pfqn, String psn, boolean hasSuper, boolean component,
                                                  Collection<? extends BaseOptionModel> options, Writer w) throws IOException {
        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import java.util.Map;\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.GeneratedPropertyConfigurer;\n");
        w.write("import org.apache.camel.spi.PropertyConfigurerGetter;\n");
        w.write("import org.apache.camel.util.CaseInsensitiveMap;\n");
        w.write("import "  + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " extends " + psn + " implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {\n");
        w.write("\n");
        if (!options.isEmpty() || !hasSuper) {

            // sort options A..Z so they always have same order
            options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());

            if (component) {
                // if its a component configurer then configuration classes are optional and we need
                // to generate a method that can lazy create a new configuration if it was null
                Optional<? extends BaseOptionModel> configurationOption = findConfiguration(options);
                if (configurationOption.isPresent()) {
                    w.write(createGetOrCreateConfiguration(en, configurationOption.get().getConfigurationClass(), configurationOption.get().getConfigurationField()));
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
                    String setterLambda = setterLambda(getOrSet, option.getJavaType(), option.getConfigurationField(), component);
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

            // generate API that returns all the options
            w.write("\n");
            w.write("    @Override\n");
            w.write("    public Map<String, Object> getAllOptions(Object target) {\n");
            if (hasSuper) {
                w.write("        Map<String, Object> answer = super.getAllOptions(target);\n");
            } else {
                w.write("        Map<String, Object> answer = new CaseInsensitiveMap();\n");
            }
            if (!options.isEmpty() || !hasSuper) {
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

            // generate API for getting a property
            w.write("\n");
            w.write("    @Override\n");
            w.write("    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        " + en + " target = (" + en + ") obj;\n");
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String getterLambda = getterLambda(getOrSet, option.getJavaType(), option.getConfigurationField(), component);
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
        }

        w.write("}\n");
        w.write("\n");
    }

    private static Optional<? extends BaseOptionModel> findConfiguration(Collection<? extends BaseOptionModel> options) {
        return options.stream().filter(o -> o.getConfigurationField() != null).findFirst();
    }

    private static String setterLambda(String getOrSet, String type, String configurationField, boolean component) {
        // type may contain generics so remove those
        if (type.indexOf('<') != -1) {
            type = type.substring(0, type.indexOf('<'));
        }
        type = type.replace('$', '.');
        if (configurationField != null) {
            if (component) {
                getOrSet = "getOrCreateConfiguration(target).set" + getOrSet;
            } else {
                getOrSet = "target.get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1) + "().set" + getOrSet;
            }
        } else {
            getOrSet = "target.set" + getOrSet;
        }

        // ((LogComponent) target).setGroupSize(property(camelContext,
        // java.lang.Integer.class, value))
        return String.format("%s(property(camelContext, %s.class, value))", getOrSet, type);
    }

    private static String getterLambda(String getOrSet, String type, String configurationField, boolean component) {
        String prefix = "boolean".equals(type) ? "is" : "get";
        if (configurationField != null) {
            if (component) {
                getOrSet = "getOrCreateConfiguration(target)." + prefix + getOrSet;
            } else {
                getOrSet = "target.get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1) + "()." + prefix + getOrSet;
            }
        } else {
            getOrSet = "target." + prefix + getOrSet;
        }

        return getOrSet + "()";
    }

    private static String createGetOrCreateConfiguration(String targetClass, String configurationClass, String configurationField) {
        String getter = "get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);
        String setter = "set" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1);

        StringBuilder sb = new StringBuilder();
        String line1 = String.format("    private %s getOrCreateConfiguration(%s target) {\n", configurationClass, targetClass);
        String line2 = String.format("        if (target.%s() == null) {\n", getter);
        String line3 = String.format("            target.%s(new %s());\n", setter, configurationClass);
        String line4 = String.format("        }\n");
        String line5 = String.format("        return target.%s();\n", getter);
        String line6 = String.format("    }\n");

        sb.append(line1).append(line2).append(line3).append(line4).append(line5).append(line6);
        return sb.toString();
    }

}
