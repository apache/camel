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

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

public final class EndpointUriFactoryGenerator {

    private EndpointUriFactoryGenerator() {
    }

    public static String generateEndpointUriFactory(String pn, String cn, String psn, ComponentModel model) {
        StringBuilder w = new StringBuilder();

        w.append("/* ").append(AbstractGeneratorMojo.GENERATED_MSG).append(" */\n");
        w.append("package ").append(pn).append(";\n");
        w.append('\n');
        w.append("import java.net.URISyntaxException;\n");
        w.append("import java.util.Collections;\n");
        w.append("import java.util.HashMap;\n");
        w.append("import java.util.HashSet;\n");
        w.append("import java.util.Map;\n");
        w.append("import java.util.Set;\n");
        w.append('\n');
        w.append("import org.apache.camel.spi.EndpointUriFactory;\n");
        w.append('\n');
        w.append("/**\n");
        w.append(" * ").append(AbstractGeneratorMojo.GENERATED_MSG).append('\n');
        w.append(" */\n");
        w.append("public class ").append(cn).append(" extends ").append(psn).append(" implements EndpointUriFactory {\n");
        w.append('\n');
        w.append("    private static final String BASE = \"").append(baseSyntax(model)).append("\";\n");

        String alternative = alternativeSchemes(model);
        if (alternative != null) {
            w.append("    private static final String[] SCHEMES = ").append(alternative).append(";\n");
        }
        w.append("\n");
        w.append("    private static final Set<String> PROPERTY_NAMES = Set.of(").append(format(generatePropertyNames(model), "            ")).append(");\n");
        w.append("    private static final Set<String> SECRET_PROPERTY_NAMES = Set.of(").append(format(generateSecretPropertyNames(model), "            ")).append(");\n");
        w.append("    private static final Set<String> MULTI_VALUE_PREFIXES = Set.of(").append(format(generateMultiValuePrefixes(model), "            ")).append(");\n");
        w.append("\n");
        w.append("    @Override\n");
        w.append("    public boolean isEnabled(String scheme) {\n");
        if (alternative == null) {
            w.append("        return \"").append(model.getScheme()).append("\".equals(scheme);\n");
        } else {
            w.append("        for (String s : SCHEMES) {\n");
            w.append("            if (s.equals(scheme)) {\n");
            w.append("                return true;\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("        return false;\n");
        }
        w.append("    }\n");
        w.append('\n');
        w.append("    @Override\n");
        w.append(
                "    public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {\n");
        w.append("        String syntax = scheme + BASE;\n");
        w.append("        String uri = syntax;\n");
        w.append('\n');
        w.append("        Map<String, Object> copy = new HashMap<>(properties);\n");
        w.append('\n');
        for (BaseOptionModel option : model.getEndpointPathOptions()) {
            w.append("        uri = buildPathParameter(syntax, uri, \"").append(option.getName())
                    .append("\", ").append(defaultValue(option)).append(", ").append(option.isRequired()).append(", copy);\n");
        }
        w.append("        uri = buildQueryParameters(uri, copy, encode);\n");
        w.append("        return uri;\n");
        w.append("    }\n");
        w.append("\n");
        w.append("    @Override\n");
        w.append("    public Set<String> propertyNames() {\n");
        w.append("        return PROPERTY_NAMES;\n");
        w.append("    }\n");
        w.append("\n");
        w.append("    @Override\n");
        w.append("    public Set<String> secretPropertyNames() {\n");
        w.append("        return SECRET_PROPERTY_NAMES;\n");
        w.append("    }\n");
        w.append("\n");
        w.append("    @Override\n");
        w.append("    public Set<String> multiValuePrefixes() {\n");
        w.append("        return MULTI_VALUE_PREFIXES;\n");
        w.append("    }\n");
        w.append("\n");
        w.append("    @Override\n");
        w.append("    public boolean isLenientProperties() {\n");
        w.append("        return ").append(model.isLenientProperties()).append(";\n");
        w.append("    }\n");
        w.append("}\n");
        w.append("\n");

        return w.toString();
    }

    private static String format(Stream<String> string, String indent)
    {
        List<String> strings = string.collect( Collectors.toList() );
        if (strings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int current = 0;
        for (String s : strings ) {
            int len = s.length();
            if (current > 0 && current + len > 120) {
                sb.append( ",\n" ).append( indent );
                current = indent.length();
            } else if (sb.length() == 0) {
                sb.append("\n").append(indent);
                current = indent.length();
            } else {
                sb.append(", ");
                current += 2;
            }
            sb.append(s);
            current += len;
        };
        return sb.toString();
    }

    private static Stream<String> generatePropertyNames(ComponentModel model) {
        return Stream.concat(
            model.getEndpointOptions().stream()
                    .map(ComponentModel.EndpointOptionModel::getName),
            // gather all the option names from the api (they can be duplicated as the same name
            // can be used by multiple methods)
            model.getApiOptions().stream()
                    .flatMap(a -> a.getMethods().stream())
                    .flatMap(m -> m.getOptions().stream())
                    .map(ComponentModel.ApiOptionModel::getName))
                // grab all, sort, unique, and join
                .sorted()
                .distinct()
                .map(s -> "\"" + s + "\"");
    }

    private static Stream<String> generateSecretPropertyNames(ComponentModel model) {
        return Stream.concat(
            model.getEndpointOptions().stream()
                    .filter(ComponentModel.EndpointOptionModel::isSecret)
                    .map(ComponentModel.EndpointOptionModel::getName),
            // gather all the option names from the api (they can be duplicated as the same name
            // can be used by multiple methods)
            model.getApiOptions().stream()
                    .flatMap(a -> a.getMethods().stream())
                    .flatMap(m -> m.getOptions().stream())
                    .filter(ComponentModel.ApiOptionModel::isSecret)
                    .map(ComponentModel.ApiOptionModel::getName))
                // grab all, sort, unique, and join
                .sorted()
                .distinct()
                .map(s -> "\"" + s + "\"");
    }

    private static Stream<String> generateMultiValuePrefixes(ComponentModel model) {
        return Stream.concat(
            model.getEndpointOptions().stream()
                    .filter(ComponentModel.EndpointOptionModel::isMultiValue)
                    .map(ComponentModel.EndpointOptionModel::getPrefix),
            // gather all the option names from the api (they can be duplicated as the same name
            // can be used by multiple methods)
            model.getApiOptions().stream()
                    .flatMap(a -> a.getMethods().stream())
                    .flatMap(m -> m.getOptions().stream())
                    .filter(ComponentModel.ApiOptionModel::isMultiValue)
                    .map(ComponentModel.ApiOptionModel::getPrefix))
                // grab all, sort, unique, and join
                .sorted()
                .distinct()
                .map(s -> "\"" + s + "\"");
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
            sb.append(sj);
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
