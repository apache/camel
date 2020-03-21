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

public final class PropertyMainConfigurerGenerator {

    private PropertyMainConfigurerGenerator() {
    }

    public static void generatePropertyConfigurer(String pn, String cn, String en,
                                                  String pfqn, String psn,
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
        if (!options.isEmpty()) {

            // sort options A..Z so they always have same order
            options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());
            w.write("    @Override\n");
            w.write("    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {\n");
            if (!options.isEmpty()) {
                w.write("        " + en + " target = (" + en + ") obj;\n");
                w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
                for (BaseOptionModel option : options) {
                    String getOrSet = option.getName();
                    getOrSet = Character.toUpperCase(getOrSet.charAt(0)) + getOrSet.substring(1);
                    String setterLambda = setterLambda(getOrSet, option.getJavaType());
                    if (!option.getName().toLowerCase().equals(option.getName())) {
                        w.write(String.format("        case \"%s\":\n", option.getName().toLowerCase()));
                    }
                    w.write(String.format("        case \"%s\": %s; return true;\n", option.getName(), setterLambda));
                }
                w.write("        default: return false;\n");
                w.write("        }\n");
            }
            w.write("    }\n");
        }
        w.write("\n");
        w.write("}\n");
        w.write("\n");
    }

    private static String setterLambda(String getOrSet, String type) {
        // type may contain generics so remove those
        if (type.indexOf('<') != -1) {
            type = type.substring(0, type.indexOf('<'));
        }
        type = type.replace('$', '.');
        getOrSet = "target.set" + getOrSet;

        // ((LogComponent) target).setGroupSize(property(camelContext,
        // java.lang.Integer.class, value))
        return String.format("%s(property(camelContext, %s.class, value))", getOrSet, type);
    }

}
