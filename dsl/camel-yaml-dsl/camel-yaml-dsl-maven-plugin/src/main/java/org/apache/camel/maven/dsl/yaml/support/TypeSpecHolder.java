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
package org.apache.camel.maven.dsl.yaml.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.squareup.javapoet.TypeSpec;

public class TypeSpecHolder {
    public final TypeSpec type;
    public final Map<String, Set<String>> attributes;

    public TypeSpecHolder(TypeSpec type, Map<String, Set<String>> attributes) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attributes, "attributes");

        this.type = type;
        this.attributes = new HashMap<>(attributes);
    }

    public static void put(Map<String, Set<String>> attributes, String key, String value) {
        attributes.computeIfAbsent(key, k -> new TreeSet<>()).add(value);
    }
}
