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
package org.apache.camel.tooling.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.tooling.model.BodyTypeModel.OptionBodyTypeModel;

/**
 * What one side of the body types of the catalog says for the options a step sets (CAMEL-24961, CAMEL-25928): the rule
 * that the YAML validator and the other tools share, so they all read the catalog the same way.
 * <p/>
 * The rule, in order:
 * <ol>
 * <li>An option the side names (in dependsOn, byOption or javaTypeOption) whose value is only known when the route
 * runs, a {@code {{placeholder}}} or a {@code ${expression}}, or that the step does not set but can be set elsewhere,
 * such as on the component: not known.</li>
 * <li>An option of dependsOn the step sets: the body of byOption for that value decides, and without one it is not
 * known.</li>
 * <li>Otherwise, the javaTypeOption the step sets: an object of the class it names, or null.</li>
 * <li>Otherwise, the bodies of byOption for the values the step sets; when two say different bodies, not known.</li>
 * <li>Otherwise, the carriers of the side; unknown is not known.</li>
 * </ol>
 * A body of byOption that has a javaTypeOption the step sets is an object of that class, or null. The values of an
 * option compare in any case, and in any style of an enum constant, as Camel takes them: convert-to-json is
 * CONVERT_TO_JSON. A side with from is the body of a side of another model, which the caller resolves.
 */
public final class BodyTypeResolver {

    private BodyTypeResolver() {
    }

    /**
     * What a side says for a step: the carriers of the body, and its Java type and format when it has them.
     */
    public record Resolved(List<String> carriers, String javaType, String format) {

        public Resolved {
            // a copy, not the list of the model the caller could change
            carriers = List.copyOf(carriers);
        }
    }

    /**
     * What a side says for the options a step sets.
     *
     * @param  side    the side, null when the catalog has none
     * @param  options the options the step sets, by name: the endpoint uri, the parameters or the options of a data
     *                 format
     * @param  hidden  the options that can be set elsewhere when the step does not set them, such as the options of the
     *                 component
     * @return         the body, or null when it is not known for the step
     */
    public static Resolved resolve(BodyTypeModel side, Map<String, String> options, Set<String> hidden) {
        if (side == null || side.getFrom() != null) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>(side.getDependsOn());
        side.getByOption().forEach(o -> names.add(o.getOption()));
        if (side.getJavaTypeOption() != null) {
            names.add(side.getJavaTypeOption());
        }
        side.getByOption().stream().map(OptionBodyTypeModel::getJavaTypeOption).filter(Objects::nonNull)
                .forEach(names::add);
        for (String name : names) {
            String value = options.get(name);
            if (value == null ? hidden.contains(name) : value.contains("{{") || value.contains("${")) {
                // it may be set elsewhere, or its value is only known when the route runs
                return null;
            }
        }
        boolean dependsOnSet = false;
        for (String name : side.getDependsOn()) {
            String value = options.get(name);
            if (value != null) {
                dependsOnSet = true;
                if (side.getByOption().stream()
                        .noneMatch(w -> w.getOption().equals(name) && isSameValue(value, w.getValue()))) {
                    // the body depends on this option, and no option value of the side says the body for its value
                    return null;
                }
            }
        }
        if (!dependsOnSet && side.getJavaTypeOption() != null && options.containsKey(side.getJavaTypeOption())) {
            return ofClass(options.get(side.getJavaTypeOption()));
        }
        Resolved matched = null;
        for (OptionBodyTypeModel when : side.getByOption()) {
            String value = options.get(when.getOption());
            if (value != null && isSameValue(value, when.getValue())) {
                Resolved resolved = when.getJavaTypeOption() != null && options.containsKey(when.getJavaTypeOption())
                        ? ofClass(options.get(when.getJavaTypeOption()))
                        : new Resolved(when.getCarriers(), when.getJavaType(), side.getFormat());
                if (resolved == null || matched != null && !matched.carriers().equals(resolved.carriers())) {
                    // two options the step sets say different bodies
                    return null;
                }
                matched = resolved;
            }
        }
        if (matched != null) {
            return matched;
        }
        if (dependsOnSet || side.isUnknown()) {
            return null;
        }
        return new Resolved(side.getCarriers(), side.getJavaType(), side.getFormat());
    }

    /**
     * The body of the class an option names: an object of it, or null when it does not convert. Object says nothing.
     */
    private static Resolved ofClass(String javaType) {
        String carrier = BodyTypeModel.carrierOf(javaType);
        if ("unknown".equals(carrier)) {
            return null;
        }
        return new Resolved(List.of(carrier, "null"), javaType.trim(), null);
    }

    /**
     * Whether a value of an option is the value a body is for: in any case, and in any style of an enum constant, as
     * the catalog and the enum type converter take it, so convert-to-json and convertToJson are CONVERT_TO_JSON.
     */
    public static boolean isSameValue(String value, String other) {
        return value.equalsIgnoreCase(other) || asEnumConstant(value).equals(asEnumConstant(other));
    }

    /** The value as an enum constant, as StringHelper.asEnumConstantValue in camel-util gives it. */
    private static String asEnumConstant(String value) {
        return camelCaseToDash(value).replaceAll("-+", "-").replace('-', '_').toUpperCase(Locale.ENGLISH);
    }

    /** As StringHelper.camelCaseToDash in camel-util, which this module does not depend on. */
    private static String camelCaseToDash(String text) {
        StringBuilder answer = new StringBuilder(text.length() + 8);
        char prev = 0;
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (ch == '-' || ch == '_') {
                answer.append('-');
            } else if (Character.isUpperCase(ch) && prev != 0) {
                char next = i < arr.length - 1 ? arr[i + 1] : 0;
                if (!Character.isUpperCase(prev) || next != 0 && Character.isLowerCase(next)) {
                    if (prev != '-' && prev != '_') {
                        answer.append('-');
                    }
                }
                answer.append(Character.toLowerCase(ch));
            } else {
                answer.append(Character.toLowerCase(ch));
            }
            prev = ch;
        }
        return answer.toString();
    }
}
