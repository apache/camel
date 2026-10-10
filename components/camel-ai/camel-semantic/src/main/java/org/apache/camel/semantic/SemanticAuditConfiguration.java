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
package org.apache.camel.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, context-wide audit configuration declared by one semantic DSL resource. */
public final class SemanticAuditConfiguration {
    public static final SemanticAuditConfiguration DISABLED
            = new SemanticAuditConfiguration(false, Map.of(), List.of("memory"), "memory", 1000, 1000);
    private final boolean enabled;
    private final Map<String, Boolean> experts;
    private final List<String> sinks;
    private final String reader;
    private final int capacity;
    private final int queueCapacity;

    public SemanticAuditConfiguration(boolean enabled, Map<String, Boolean> experts, List<String> sinks,
                                      String reader, int capacity, int queueCapacity) {
        this.enabled = enabled;
        this.experts = Map.copyOf(experts);
        this.sinks = List.copyOf(sinks);
        this.reader = Objects.requireNonNull(reader);
        if (capacity < 1 || capacity > 100000 || queueCapacity < 1 || queueCapacity > 100000
                || sinks.isEmpty() || sinks.stream().anyMatch(String::isBlank)
                || sinks.stream().distinct().count() != sinks.size() || reader.isBlank()
                || experts.keySet().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "Invalid semantic audit configuration: require unique sinks, nonblank references and capacities between 1 and 100000");
        }
        if ("memory".equals(reader) && !sinks.contains("memory")) {
            throw new IllegalArgumentException("Semantic audit reader 'memory' requires the 'memory' sink");
        }
        this.capacity = capacity;
        this.queueCapacity = queueCapacity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabled(String expert) {
        return expert == null ? enabled : experts.getOrDefault(expert, enabled);
    }

    public Map<String, Boolean> getExperts() {
        return experts;
    }

    public List<String> getSinks() {
        return sinks;
    }

    public String getReader() {
        return reader;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SemanticAuditConfiguration c && enabled == c.enabled && experts.equals(c.experts)
                && sinks.equals(c.sinks) && reader.equals(c.reader) && capacity == c.capacity
                && queueCapacity == c.queueCapacity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, experts, sinks, reader, capacity, queueCapacity);
    }
}
