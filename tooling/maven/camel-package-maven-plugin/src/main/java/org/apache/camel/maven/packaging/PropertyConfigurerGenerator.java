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

import org.apache.camel.tooling.model.BaseOptionModel;

public final class PropertyConfigurerGenerator {

    private PropertyConfigurerGenerator() {
    }

    public static void generatePropertyConfigurer(String pn, String cn, String en,
                                                  String pfqn, String psn, boolean hasSuper,
                                                  Collection<? extends BaseOptionModel> options, Writer w) throws IOException {
        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.GeneratedPropertyConfigurer;\n");
        w.write("import "  + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " extends " + psn + " implements GeneratedPropertyConfigurer {\n");
        w.write("\n");
        if (!options.isEmpty() || !hasSuper) {
            w.write("    @Override\n");
            w.write("    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        " + en + " target = (" + en + ") obj;\n");
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String setterLambda = setterLambda(getOrSet, option.getJavaType(), option.getConfigurationField());
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
        }
        w.write("\n");
        w.write("}\n");
        w.write("\n");
    }

    private static String setterLambda(String getOrSet, String type, String configurationField) {
        // type may contain generics so remove those
        if (type.indexOf('<') != -1) {
            type = type.substring(0, type.indexOf('<'));
        }
        type = type.replace('$', '.');
        if (configurationField != null) {
            getOrSet = "get" + Character.toUpperCase(configurationField.charAt(0)) + configurationField.substring(1) + "().set" + getOrSet;
        } else {
            getOrSet = "set" + getOrSet;
        }

        // ((LogComponent) target).setGroupSize(property(camelContext,
        // java.lang.Integer.class, value))
        return String.format("target.%s(property(camelContext, %s.class, value))", getOrSet, type);
    }

}
