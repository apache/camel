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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.StringHelper;

/**
 * The traits that {@code --trait} configures, and their options, as the trait model declares them: the names are the
 * kebab-case of the model properties, which {@link TraitHelper#parseTraits(String[])} reads back.
 */
public final class TraitOptions {

    // addons are not a trait, and no trait applies service-binding
    private static final Set<String> NOT_LISTED = Set.of("addons", "service-binding");

    /**
     * A trait with its options.
     */
    public record TraitInfo(String name, String description, List<OptionInfo> options) {
    }

    /**
     * An option of a trait: its type is {@code bool}, {@code int}, {@code string}, {@code []string} (several values,
     * separated by commas or given with several {@code --trait}) or {@code map} ({@code key=value}), and the values it
     * takes when it is a choice.
     */
    public record OptionInfo(String name, String type, String description, List<String> values) {
    }

    private TraitOptions() {
    }

    /**
     * The traits, in the order of the model.
     */
    public static List<TraitInfo> traits() {
        List<TraitInfo> answer = new ArrayList<>();
        for (Field field : Traits.class.getDeclaredFields()) {
            JsonProperty property = field.getAnnotation(JsonProperty.class);
            if (property == null || NOT_LISTED.contains(property.value())) {
                continue;
            }
            Class<?> model = field.getType();
            String description = Optional.ofNullable(model.getAnnotation(JsonClassDescription.class))
                    .map(JsonClassDescription::value)
                    .orElseGet(() -> description(field));
            answer.add(new TraitInfo(property.value(), description, options(model)));
        }
        return answer;
    }

    /**
     * The trait of the given name.
     */
    public static Optional<TraitInfo> trait(String name) {
        return traits().stream().filter(t -> t.name().equals(name)).findFirst();
    }

    /**
     * The start of every trait option, such as {@code container.port=}, for the completion of {@code --trait}.
     */
    public static List<String> completionCandidates() {
        List<String> answer = new ArrayList<>();
        for (TraitInfo trait : traits()) {
            for (OptionInfo option : trait.options()) {
                answer.add(trait.name() + "." + option.name() + "=");
            }
        }
        return answer;
    }

    private static List<OptionInfo> options(Class<?> model) {
        List<OptionInfo> answer = new ArrayList<>();
        for (Field field : model.getDeclaredFields()) {
            JsonProperty property = field.getAnnotation(JsonProperty.class);
            if (property == null) {
                continue;
            }
            answer.add(new OptionInfo(
                    StringHelper.camelCaseToDash(property.value()), type(field), description(field), values(field.getType())));
        }
        return answer;
    }

    private static String description(Field field) {
        JsonPropertyDescription description = field.getAnnotation(JsonPropertyDescription.class);
        return description != null ? description.value() : "";
    }

    private static String type(Field field) {
        Class<?> type = field.getType();
        if (type == Boolean.class || type == boolean.class) {
            return "bool";
        }
        if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
            return "int";
        }
        if (List.class.isAssignableFrom(type)) {
            Type generic = field.getGenericType();
            if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments()[0] instanceof Class<?> item) {
                return "[]" + (item == String.class ? "string" : item.getSimpleName().toLowerCase());
            }
            return "[]string";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "map";
        }
        return "string";
    }

    private static List<String> values(Class<?> type) {
        if (!type.isEnum()) {
            return List.of();
        }
        List<String> answer = new ArrayList<>();
        for (Field constant : type.getDeclaredFields()) {
            if (constant.isEnumConstant()) {
                JsonProperty property = constant.getAnnotation(JsonProperty.class);
                answer.add(property != null ? property.value() : constant.getName());
            }
        }
        return answer;
    }
}
