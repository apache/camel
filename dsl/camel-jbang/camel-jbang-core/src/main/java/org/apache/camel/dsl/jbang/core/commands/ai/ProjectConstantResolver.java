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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.java.in.ConstantResolver;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * The constants a Java route refers to that the parser cannot see (CAMEL-25148): those of the other source files of the
 * project ({@code Application.QUEUE}), and the header constants of Camel's components, which the catalog records
 * ({@code KafkaConstants.KEY} is {@code kafka.KEY}), and the values of their enums ({@code InfinispanOperation.PUT}),
 * so no component needs to be on the class path. The files are read, and the catalog's headers indexed, only when a
 * constant is asked for.
 */
final class ProjectConstantResolver implements ConstantResolver {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static volatile Map<String, String> headerConstants;

    private final Map<String, List<Supplier<String>>> sources = new HashMap<>();

    /** A source file as read: its package and its constants. */
    private record Read(String pkg, Map<String, Object> constants) {
    }

    private final Map<Supplier<String>, Read> read = new HashMap<>();
    private final CamelCatalog catalog;

    /**
     * @param javaSources the project's Java sources by path, read when first needed
     * @param catalog     for the component header constants, may be null
     */
    ProjectConstantResolver(Map<String, Supplier<String>> javaSources, CamelCatalog catalog) {
        this.catalog = catalog;
        javaSources.forEach((path, content) -> {
            String file = path.substring(path.lastIndexOf('/') + 1);
            if (file.endsWith(".java")) {
                sources.computeIfAbsent(file.substring(0, file.length() - 5), k -> new ArrayList<>()).add(content);
            }
        });
    }

    @Override
    public Object constant(String className, String field) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        String pkg = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : null;
        for (Supplier<String> source : sources.getOrDefault(simple, List.of())) {
            Read r = read.computeIfAbsent(source, s -> {
                String content = s.get();
                if (content == null) {
                    return new Read(null, Map.of());
                }
                Matcher m = PACKAGE.matcher(content);
                return new Read(m.find() ? m.group(1) : null, LwJavaParser.constants(content));
            });
            if (pkg != null && r.pkg() != null && !pkg.equals(r.pkg())) {
                // a class of the same name in another package
                continue;
            }
            Object v = r.constants().get(field);
            if (v != null) {
                return v;
            }
        }
        if (catalog == null) {
            return null;
        }
        Map<String, String> headers = headerConstants(catalog);
        String v = headers.get(className + "#" + field);
        // an enum constant PUT_IF_ABSENT for the option value putIfAbsent
        return v != null ? v : headers.get(className + "#" + enumKey(field));
    }

    /** An enum value as a key that ignores case and underscores: PUT_IF_ABSENT and putIfAbsent alike. */
    private static String enumKey(String value) {
        return "~" + value.replace("_", "").toLowerCase(Locale.ROOT);
    }

    /** The values of an option or header whose type is an enum of the component: {@code InfinispanOperation#PUT}. */
    private static void enums(Map<String, String> answer, BaseOptionModel o) {
        if (o.getEnums() != null && o.getJavaType() != null && o.getJavaType().contains(".")) {
            for (String value : o.getEnums()) {
                answer.putIfAbsent(o.getJavaType() + "#" + value, value);
                answer.putIfAbsent(o.getJavaType() + "#" + enumKey(value), value);
            }
        }
    }

    /** Every component's header constants by class and field ({@code ...KafkaConstants#KEY}), and the header name. */
    static Map<String, String> headerConstants(CamelCatalog catalog) {
        Map<String, String> answer = headerConstants;
        if (answer == null) {
            Map<String, String> built = new HashMap<>();
            answer = built;
            for (String name : catalog.findComponentNames()) {
                ComponentModel model = catalog.componentModel(name);
                if (model == null) {
                    continue;
                }
                for (ComponentModel.EndpointHeaderModel h : model.getEndpointHeaders()) {
                    if (h.getConstantName() != null) {
                        built.putIfAbsent(h.getConstantName(), h.getName());
                    }
                    enums(built, h);
                }
                model.getEndpointOptions().forEach(o -> enums(built, o));
                model.getComponentOptions().forEach(o -> enums(built, o));
            }
            headerConstants = answer;
        }
        return answer;
    }
}
