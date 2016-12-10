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
import java.util.regex.Pattern;

import org.apache.camel.util.ObjectHelper;

public final class TypeFilters {
    private TypeFilters() {
    }

    public static final class RegExp implements TypeFilter {
        private final List<Pattern> patterns;

        public RegExp(String pattern) {
            this.patterns = Collections.singletonList(Pattern.compile(pattern));
        }

        public RegExp(Collection<String> patterns) {            
            this.patterns = new ArrayList<>(patterns.size());
            for (String pattern : patterns) {
                this.patterns.add(Pattern.compile(pattern));
            }
        }

        @Override
        public boolean test(String type) {
            for (Pattern pattern : this.patterns) {
                if (pattern.matcher(type).matches()) {
                    return true;
                }
            }

            return false;
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

    public static TypeFilter valueOf(String definition) {
        String type = ObjectHelper.before(definition, ":");
        if (type == null || "type".equals(type)) {
            String val = ObjectHelper.after(definition, ":");
            if (val != null) {
                return new TypeName(val);
            }
        } else if ("regexp".equals(type)) {
            String val = ObjectHelper.after(definition, ":");
            if (val != null) {
                return new RegExp(val);
            }
        }

        return null;
    }

    public static TypeFilter regexp(String... patterns) {
        return new RegExp(Arrays.asList(patterns));
    }

    public static TypeFilter regexp(Collection<String> patterns) {
        return new RegExp(patterns);
    }

    public static TypeFilter typeNames(Collection<String> types) {
        return new TypeName(types);
    }

    public static TypeFilter typeNames(String... types) {
        return typeNames(Arrays.asList(types));
    }

    public static TypeFilter types(Collection<Class<?>> types) {
        List<String> filters = new ArrayList<>(types.size());
        for (Class<?> type : types) {
            filters.add(type.getName());
        }

        return new TypeName(filters);
    }

    public static TypeFilter types(Class<?>... types) {
        return types(Arrays.asList(types));
    }

    public static TypeFilter allowAll() {
        return new TypeFilter() {
            @Override
            public boolean test(String type) {
                return true;
            }
        };
    }

    public static TypeFilter allowNone() {
        return new TypeFilter() {
            @Override
            public boolean test(String type) {
                return false;
            }
        };
    }
}
