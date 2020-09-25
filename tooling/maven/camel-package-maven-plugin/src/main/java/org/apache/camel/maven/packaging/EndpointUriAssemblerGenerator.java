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
import java.util.StringJoiner;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

public final class EndpointUriAssemblerGenerator {

    private EndpointUriAssemblerGenerator() {
    }

    public static void generateEndpointUriAssembler(
            String pn, String cn, String en,
            String pfqn, String psn, ComponentModel model, Writer w)
            throws IOException {

        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import java.net.URISyntaxException;\n");
        w.write("import java.util.HashMap;\n");
        w.write("import java.util.Map;\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.EndpointUriAssembler;\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("public class " + cn + " extends " + psn + " implements EndpointUriAssembler {\n");
        w.write("\n");
        w.write("    private static final String BASE = \"" + baseSyntax(model) + "\";\n");

        String alternative = alternativeSchemes(model);
        if (alternative != null) {
            w.write("    private static final String[] SCHEMES = " + alternative + ";\n");
        }
        w.write("\n");
        w.write("    @Override\n");
        w.write("    public boolean isEnabled(String scheme) {\n");
        if (alternative == null) {
            w.write("        return \"" + model.getScheme() + "\".equals(scheme);\n");
        } else {
            w.write("        for (String s : SCHEMES) {\n");
            w.write("            if (s.equals(scheme)) {\n");
            w.write("                return true;\n");
            w.write("            }\n");
            w.write("        }\n");
            w.write("        return false;\n");
        }
        w.write("    }\n");
        w.write("\n");
        w.write("    @Override\n");
        w.write("    public String buildUri(CamelContext camelContext, String scheme, Map<String, Object> parameters) throws URISyntaxException {\n");
        w.write("        String syntax = scheme + BASE;\n");
        w.write("        String uri = syntax;\n");
        w.write("\n");
        w.write("        Map<String, Object> copy = new HashMap<>(parameters);\n");
        w.write("\n");
        for (BaseOptionModel option : model.getEndpointPathOptions()) {
            w.write("        uri = buildPathParameter(camelContext, syntax, uri, \"" + option.getName() + "\", "
                    + defaultValue(option) + ", " + option.isRequired() + ", copy);\n");
        }
        w.write("        uri = buildQueryParameters(camelContext, uri, copy);\n");
        w.write("        return uri;\n");
        w.write("    }\n");
        w.write("}\n");
        w.write("\n");
    }

    private static String alternativeSchemes(ComponentModel model) {
        StringBuilder sb = new StringBuilder();
        if (model.getAlternativeSchemes() != null) {
            sb.append("new String[]{");
            String[] alts = model.getAlternativeSchemes().split(",");
            StringJoiner sj = new StringJoiner(", ");
            for (String alt : alts) {
                sj.add("\"" + alt + "\"");
            }
            sb.append(sj.toString());
            sb.append("}");
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    private static String baseSyntax(ComponentModel model) {
        String base = model.getSyntax();
        base = base.replaceFirst(model.getScheme(), "");
        return base;
    }

    private static Object defaultValue(BaseOptionModel option) {
        Object obj = option.getDefaultValue();
        if (obj instanceof String) {
            return "\"" + obj + "\"";
        } else {
            return obj;
        }
    }

}
