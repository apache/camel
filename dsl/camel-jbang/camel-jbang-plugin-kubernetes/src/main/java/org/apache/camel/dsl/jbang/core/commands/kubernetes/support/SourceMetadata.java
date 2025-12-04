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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class SourceMetadata {

    @JsonProperty
    public final Reources resources = new Reources();

    @JsonProperty
    public final Endpoints endpoints = new Endpoints();

    @JsonProperty
    public final List<Capability> capabilities = new ArrayList<>();

    @JsonProperty
    public final Set<String> dependencies = new TreeSet<>();

    @JsonPropertyOrder(alphabetic = true)
    public static class Reources {
        @JsonProperty
        public final Set<String> components = new TreeSet<>();

        @JsonProperty
        public final Set<String> languages = new TreeSet<>();

        @JsonProperty
        public final Set<String> dataformats = new TreeSet<>();

        @JsonProperty
        public final Set<String> kamelets = new TreeSet<>();
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class Endpoints {
        @JsonProperty
        public final Set<String> from = new TreeSet<>();

        @JsonProperty
        public final Set<String> to = new TreeSet<>();
    }
}
