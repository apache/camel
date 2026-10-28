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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.spi.BodyTypeRegistry;

/**
 * Default {@link BodyTypeRegistry}: per route, per node, the distinct body types and their kinds in the order first
 * seen.
 */
public class DefaultBodyTypeRegistry implements BodyTypeRegistry {

    /**
     * The most distinct body types a node keeps: a node is nearly always reached with one or two types, and more than a
     * few says enough (the type varies).
     */
    static final int MAX_TYPES_PER_NODE = 8;

    private final Map<String, Map<String, NodeTypes>> routes = new ConcurrentHashMap<>();

    @Override
    public void addBodyType(String routeId, String nodeId, String bodyType, Kind kind) {
        // called for every node an exchange reaches while the message history is on: plain lookups first, and no
        // allocation when the node has seen the type before
        Map<String, NodeTypes> nodes = routes.get(routeId);
        if (nodes == null) {
            nodes = routes.computeIfAbsent(routeId, k -> new ConcurrentHashMap<>());
        }
        NodeTypes types = nodes.get(nodeId);
        if (types == null) {
            types = nodes.computeIfAbsent(nodeId, k -> new NodeTypes());
        }
        types.add(bodyType, kind);
    }

    @Override
    public List<String> getBodyTypes(String routeId, String nodeId) {
        NodeTypes types = node(routeId, nodeId);
        return types != null ? types.types(null) : List.of();
    }

    @Override
    public List<Kind> getBodyKinds(String routeId, String nodeId) {
        NodeTypes types = node(routeId, nodeId);
        return types != null ? List.of(types.kinds) : List.of();
    }

    @Override
    public List<String> getBodyTypes(String routeId, String nodeId, Kind kind) {
        NodeTypes types = node(routeId, nodeId);
        return types != null && kind != null ? types.types(kind) : List.of();
    }

    @Override
    public Map<String, List<String>> getBodyTypes(String routeId) {
        Map<String, NodeTypes> nodes = routes.get(routeId);
        if (nodes == null) {
            return Map.of();
        }
        Map<String, List<String>> answer = new LinkedHashMap<>();
        nodes.forEach((id, types) -> answer.put(id, types.types(null)));
        return answer;
    }

    @Override
    public void removeRoute(String routeId) {
        routes.remove(routeId);
    }

    @Override
    public void clear() {
        routes.clear();
    }

    private NodeTypes node(String routeId, String nodeId) {
        Map<String, NodeTypes> nodes = routes.get(routeId);
        return nodes != null ? nodes.get(nodeId) : null;
    }

    /**
     * A body type and its kind.
     */
    private record TypeKind(String type, Kind kind) {
    }

    /**
     * The distinct types and kinds of one node, as small arrays that are replaced when a new one is seen: reading them
     * (on every exchange) takes no lock.
     */
    private static final class NodeTypes {

        private volatile TypeKind[] types = new TypeKind[0];
        private volatile Kind[] kinds = new Kind[0];

        void add(String type, Kind kind) {
            TypeKind[] known = types;
            // a node keeps every kind, also of a type beyond the first few: a new kind is what needs attention
            if (containsType(known, type)
                    || known.length >= MAX_TYPES_PER_NODE && (kind == null || containsKind(kinds, kind))) {
                return;
            }
            synchronized (this) {
                if (kind != null && !containsKind(kinds, kind)) {
                    Kind[] next = Arrays.copyOf(kinds, kinds.length + 1);
                    next[kinds.length] = kind;
                    kinds = next;
                }
                TypeKind[] current = types;
                if (!containsType(current, type) && current.length < MAX_TYPES_PER_NODE) {
                    TypeKind[] next = Arrays.copyOf(current, current.length + 1);
                    next[current.length] = new TypeKind(type, kind);
                    types = next;
                }
            }
        }

        /**
         * The types, of the kind only when one is given.
         */
        List<String> types(Kind kind) {
            List<String> answer = new ArrayList<>();
            for (TypeKind t : types) {
                if (kind == null || kind == t.kind()) {
                    answer.add(t.type());
                }
            }
            return Collections.unmodifiableList(answer);
        }

        private static boolean containsType(TypeKind[] types, String type) {
            for (TypeKind t : types) {
                if (t.type().equals(type)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsKind(Kind[] kinds, Kind kind) {
            for (Kind k : kinds) {
                if (k == kind) {
                    return true;
                }
            }
            return false;
        }
    }
}
