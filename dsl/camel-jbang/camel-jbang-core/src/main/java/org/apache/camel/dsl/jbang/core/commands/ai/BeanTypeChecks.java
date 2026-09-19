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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.stripComment;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.unquote;

/**
 * Checks how each bean declared under {@code beans:} is created, for the classes the validator can load (the JDK and
 * the CLI classpath): a class with no public no-arg constructor that has a {@code builder()} or {@code newBuilder()}
 * method is created through that builder (CAMEL-24820), so its {@code properties} are checked against what the builder
 * accepts, which are not the names of the bean; and a class with neither a constructor nor a builder is reported with
 * the ways it can be created (constructor arguments, a factory method, a builder class), the same text the runtime
 * gives, before the run. A class the validator cannot load (a dependency camel run downloads) is left alone.
 */
final class BeanTypeChecks {

    private static final Pattern ITEM_PATTERN = Pattern.compile("^(\\s*)-\\s+(\\w+):\\s*(.*)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^(\\s*)(\\w+):\\s*(.*)$");
    private static final Pattern BEANS_PATTERN = Pattern.compile("^\\s*-?\\s*beans:\\s*$");

    private BeanTypeChecks() {
    }

    /** A bean declaration: its fields with their lines, and its property keys with their lines. */
    private static final class Declaration {
        final Map<String, String> fields = new LinkedHashMap<>();
        final Map<String, Integer> fieldLines = new LinkedHashMap<>();
        final Map<String, Integer> properties = new LinkedHashMap<>();
        boolean constructors;
    }

    public static List<String> validateBeanTypes(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        for (Declaration bean : declarations(content.split("\n", -1))) {
            check(bean, msgs);
        }
        return msgs;
    }

    /**
     * The bean declarations of the {@code beans:} blocks: each list item is a bean, its keys are the fields at the
     * item's indent, and the keys under {@code properties:} are the properties.
     */
    private static List<Declaration> declarations(String[] lines) {
        List<Declaration> answer = new ArrayList<>();
        int beansIndent = -1;
        Declaration current = null;
        int itemIndent = -1;
        String block = null;
        int blockIndent = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (BEANS_PATTERN.matcher(line).matches()) {
                beansIndent = indent;
                current = null;
                continue;
            }
            if (beansIndent < 0) {
                continue;
            }
            if (indent <= beansIndent) {
                // the beans block ended
                beansIndent = -1;
                current = null;
                continue;
            }
            Matcher item = ITEM_PATTERN.matcher(line);
            if (item.matches() && (current == null || indent <= itemIndent)) {
                current = new Declaration();
                answer.add(current);
                itemIndent = indent;
                block = null;
                // the first field is on the item line
                block = field(current, i, item.group(2), item.group(3));
                blockIndent = itemIndent + 2;
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher field = FIELD_PATTERN.matcher(line);
            if (!field.matches()) {
                continue;
            }
            int fieldIndent = itemIndent + 2;
            if (indent == fieldIndent) {
                block = field(current, i, field.group(2), field.group(3));
                blockIndent = indent;
            } else if (block != null && indent > blockIndent) {
                if ("properties".equals(block) && indent == blockIndent + 2) {
                    current.properties.put(field.group(2), i + 1);
                } else if ("constructors".equals(block)) {
                    current.constructors = true;
                }
            }
        }
        return answer;
    }

    /** Records a field of the declaration; returns the name of the block the field opens (properties, constructors). */
    private static String field(Declaration bean, int lineIdx, String key, String value) {
        String v = unquote(stripComment(value).trim());
        bean.fields.put(key, v);
        bean.fieldLines.put(key, lineIdx + 1);
        if ("properties".equals(key) || "constructors".equals(key)) {
            return v.isEmpty() ? key : null;
        }
        return null;
    }

    private static void check(Declaration bean, List<String> msgs) {
        String type = bean.fields.get("type");
        Integer typeLine = bean.fieldLines.get("type");
        if (type == null || typeLine == null || type.contains("{{") || type.contains("${")) {
            return;
        }
        if (type.startsWith("#class:")) {
            type = type.substring(7);
        } else if (type.startsWith("#")) {
            // #bean: or #type: refer to an existing bean
            return;
        }
        boolean explicit = bean.fields.containsKey("builderClass") || bean.fields.containsKey("factoryMethod")
                || bean.fields.containsKey("factoryBean") || bean.fields.containsKey("scriptLanguage") || bean.constructors
                || type.contains("#") || type.contains("(");
        if (explicit) {
            return;
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(type, false, SourceValidator.class.getClassLoader());
        } catch (Throwable e) {
            // not a class the validator can see: the runtime has the dependency
            return;
        }
        Class<?> builder = PropertyBindingSupport.builderType(clazz);
        if (builder == null) {
            String hint = PropertyBindingSupport.noPublicConstructorHint(clazz);
            if (hint != null) {
                msgs.add("Line " + typeLine + ": type: " + hint);
            }
            return;
        }
        // the bean is created through its builder: the properties are the builder's, and the bean's setters
        List<String> builderProperties = PropertyBindingSupport.builderPropertyNames(builder);
        List<String> beanProperties = PropertyBindingSupport.setterPropertyNames(clazz);
        String builderMethod = bean.fields.get("builderMethod");
        if (builderMethod != null && !hasPublicNoArgMethod(builder, builderMethod)) {
            msgs.add("Line " + bean.fieldLines.get("builderMethod") + ": builderMethod: " + builder.getName()
                     + " has no public no-arg method " + builderMethod + "(); its methods returning " + clazz.getSimpleName()
                     + " are: " + String.join(", ", methodsReturning(builder, clazz)));
        }
        for (Map.Entry<String, Integer> property : bean.properties.entrySet()) {
            String key = property.getKey();
            String name = StringHelper.dashToCamelCase(key);
            if (builderProperties.contains(name) || beanProperties.contains(name)) {
                continue;
            }
            String suggestion = "";
            for (String candidate : builderProperties) {
                if (candidate.equalsIgnoreCase(name)) {
                    suggestion = " (did you mean " + candidate + "?)";
                }
            }
            if (suggestion.isEmpty()) {
                // a setter of the created bean is as valid a property as one of the builder
                for (String candidate : beanProperties) {
                    if (candidate.equalsIgnoreCase(name)) {
                        suggestion = " (did you mean " + candidate + "?)";
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(property.getValue()).append(": ").append(key).append(": unknown property of ")
                    .append(clazz.getName()).append(suggestion).append(", which is created through its builder ")
                    .append(builder.getName()).append("; the builder accepts: ").append(String.join(", ", builderProperties));
            if (!beanProperties.isEmpty()) {
                sb.append("; the created bean accepts: ").append(String.join(", ", beanProperties));
            }
            msgs.add(sb.toString());
        }
    }

    private static boolean hasPublicNoArgMethod(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            return !Modifier.isStatic(m.getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static List<String> methodsReturning(Class<?> builder, Class<?> type) {
        List<String> answer = new ArrayList<>();
        for (Method m : builder.getMethods()) {
            if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers())
                    && type.isAssignableFrom(m.getReturnType())) {
                answer.add(m.getName());
            }
        }
        answer.sort(String.CASE_INSENSITIVE_ORDER);
        return answer;
    }
}
