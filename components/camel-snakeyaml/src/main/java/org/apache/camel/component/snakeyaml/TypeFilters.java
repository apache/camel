/**
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

package org.apache.camel.component.snakeyaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.util.StringHelper;

public final class TypeFilters {
    private TypeFilters() {
    }

    public static final class RegExp implements TypeFilter {
        private final List<Predicate<String>> predicates;

        public RegExp(String pattern) {
            this.predicates = Collections.singletonList(Pattern.compile(pattern).asPredicate());
        }

        public RegExp(Collection<String> patterns) {
            this.predicates = patterns.stream()
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .collect(Collectors.toList());
        }

        @Override
        public boolean test(String type) {
            return predicates.stream().anyMatch(p -> p.test(type));
        }
    }

    public static final class TypeName implements TypeFilter {
        private final List<String> values;

        public TypeName(String value) {
            this.values = Collections.singletonList(value);
        }

        public TypeName(Collection<String> values) {
            this.values = new ArrayList<>(values);
        }

        @Override
        public boolean test(String type) {
            return this.values.contains(type);
        }
    }

    // ***************************
    // Helpers
    // ***************************

    public static Optional<TypeFilter> valueOf(String definition) {
        String type = StringHelper.before(definition, ":");
        if (type == null || "type".equals(type)) {
            return StringHelper.after(definition, ":", TypeName::new);
        } else if ("regexp".equals(type)) {
            return StringHelper.after(definition, ":", RegExp::new);
        }

        return Optional.empty();
    }

    public static TypeFilter regexp(String... patterns) {
        return new RegExp(Arrays.asList(patterns));
    }

    public static TypeFilter regexp(Collection<String> patterns) {
        return new RegExp(patterns);
    }

    public static TypeFilter typeNames(Collection<String> values) {
        return new TypeName(values);
    }

    public static TypeFilter typeNames(String... values) {
        return typeNames(Arrays.asList(values));
    }

    public static TypeFilter types(Collection<Class<?>> values) {
        return new TypeName(values.stream().map(c -> c.getName()).collect(Collectors.toList()));
    }

    public static TypeFilter types(Class<?>... values) {
        return types(Arrays.asList(values));
    }

    public static TypeFilter allowAll() {
        return s -> true;
    }

    public static TypeFilter allowNone() {
        return s -> false;
    }
}
