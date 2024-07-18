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

package org.apache.camel.builder;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.KameletSpec;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Special routes builder representing a Kamelet definition. Each Kamelet defines a set of metadata (name, title,
 * properties) that get exposed to users as a for of specification. The Kamelet provides a route template that uses the
 * exposed properties to implement an opinionated route that serves a very specific use case.
 */
public abstract class KameletRouteTemplate extends RouteBuilder {

    protected final KameletType type;

    public KameletRouteTemplate() {
        if (!this.getClass().isAnnotationPresent(KameletSpec.class)) {
            throw new RuntimeCamelException(
                    "Missing Kamelet definition annotation on Kamelet type: %s".formatted(this.getClass().getName()));
        }

        KameletSpec annotation = this.getClass().getAnnotation(KameletSpec.class);
        if (ObjectHelper.isEmpty(annotation.name())) {
            throw new RuntimeCamelException(
                    "Missing Kamelet name on type: %s".formatted(this.getClass().getName()));
        }

        type = KameletType.from(annotation.name(), this.getClass());
    }

    protected abstract void template(RouteTemplateDefinition template);

    public final void configure() {
        RouteTemplateDefinition templateDefinition = routeTemplate(getId());
        collectKameletProperties().forEach(kameletProperty -> {
            if (kameletProperty.required()) {
                templateDefinition.templateParameter(kameletProperty.name(), kameletProperty.defaultValue(),
                        kameletProperty.description());
            } else {
                templateDefinition.templateOptionalParameter(kameletProperty.name(), kameletProperty.defaultValue(),
                        kameletProperty.description());
            }
        });
        template(templateDefinition);
    }

    /**
     * Subclasses may overwrite this method to add Kamelet properties to the route template definition.
     *
     * @return
     */
    protected KameletProperty[] properties() {
        return null;
    }

    /**
     * Retrieves all Kamelet properties set on the KameletDefinition annotation or via properties builder.
     */
    private Stream<KameletProperty> collectKameletProperties() {
        return Stream.concat(
                Arrays.stream(this.getClass().getAnnotation(KameletSpec.class).properties())
                        .map(KameletProperty::ofAnnotation),
                Optional.ofNullable(properties()).stream().flatMap(Stream::of));
    }

    protected String kameletSource() {
        return "kamelet:source";
    }

    protected String kameletSink() {
        return "kamelet:sink";
    }

    protected String getId() {
        KameletSpec annotation = this.getClass().getAnnotation(KameletSpec.class);
        return annotation.name();
    }

    public record KameletProperty(String name,
            String title,
            String type,
            boolean required,
            String description,
            String defaultValue,
            String[] enumeration,
            String example) {

        public KameletProperty(String name, String title, String description) {
            this(name, title, "string", false, description, null, new String[] {}, null);
        }

        private static KameletProperty ofAnnotation(org.apache.camel.spi.KameletProperty annotation) {
            return new KameletProperty(
                    annotation.name(), annotation.title(), annotation.type(), annotation.required(),
                    annotation.description(), annotation.defaultValue(), annotation.enumeration(), annotation.example());
        }
    }

    public enum KameletType {
        SOURCE,
        SINK,
        ACTION;

        static KameletType from(String name, Class<?> kameletType) {
            String type = StringHelper.afterLast(name, "-");

            boolean valid = Arrays.stream(values())
                    .map(KameletType::name)
                    .anyMatch(t -> t.equals(type.toUpperCase(Locale.US)));

            if (!valid) {
                throw new RuntimeCamelException(
                        "Invalid Kamelet name '%s' on Kamelet type: %s - must be one of %s".formatted(name,
                                kameletType.getName(),
                                Arrays.stream(values())
                                        .map(KameletType::name)
                                        .map(String::toLowerCase)
                                        .map("*-%s"::formatted)
                                        .collect(Collectors.joining(", "))));
            }

            return KameletType.valueOf(type.toUpperCase(Locale.US));
        }
    }
}
