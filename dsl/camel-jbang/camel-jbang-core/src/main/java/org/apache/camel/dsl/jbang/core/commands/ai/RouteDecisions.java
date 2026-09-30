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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The decision points of a route (CAMEL-25161): where a message takes one way or another, or is split, collected or
 * retried. These get a plain-language label from the AI project overview, as their expressions say little to a reader
 * who does not know the language.
 * <p>
 * A decision point is addressed by its path in the route, such as {@code choice[1]/when[2]} or
 * {@code split[1]/filter[1]}: each segment is a decision point and its position among the decision points of the same
 * kind under the same parent. Other steps do not count, so adding a {@code to} or a {@code log} does not change a path.
 * The source (YAML, XML and Java, read by the project overview) and the running route tree (the TUI) give the same
 * paths, as both nest the steps the same way.
 */
public final class RouteDecisions {

    /** The kinds of decision points, as the EIP names of the DSLs and the running route tree have them. */
    public static final Set<String> TYPES = Set.of(
            "choice", "when", "otherwise", "filter", "split", "aggregate", "loop", "doTry", "doCatch", "circuitBreaker");

    /** At most this many decision points of a route are collected, so the prompt stays small for local models. */
    public static final int MAX_PER_ROUTE = 10;

    /**
     * A decision point of a route.
     *
     * @param path       its path in the route, such as {@code choice[1]/when[2]}
     * @param type       the kind of EIP, such as {@code when}
     * @param expression what it decides on, such as {@code simple: ${header.x} > 5}; may be null
     * @param line       where it is written, from 1; 0 when not known
     */
    public record DecisionPoint(String path, String type, String expression, int line) {
    }

    private RouteDecisions() {
    }

    /** Whether a kind of decision point decides on an expression (or exception types): not choice or doTry. */
    public static boolean hasExpression(String type) {
        return !"choice".equals(type) && !"otherwise".equals(type) && !"doTry".equals(type)
                && !"circuitBreaker".equals(type);
    }

    /**
     * Where the decision points below a step are: the path of the nearest decision point above, and how many of each
     * kind were seen below it so far.
     */
    public static final class Scope {

        private final String path;
        private final Map<String, Integer> counts = new HashMap<>();

        private Scope(String path) {
            this.path = path;
        }

        /** The scope of a route. */
        public static Scope route() {
            return new Scope("");
        }

        /** The path of the next decision point of this kind, and the scope of the steps below it. */
        public Scope child(String type) {
            int n = counts.merge(type, 1, Integer::sum);
            String segment = type + "[" + n + "]";
            return new Scope(path.isEmpty() ? segment : path + "/" + segment);
        }

        public String path() {
            return path;
        }
    }

    /** Adds a decision point unless the route already has the most it keeps. */
    static void add(List<DecisionPoint> list, Scope scope, String type, String expression, int line) {
        if (list.size() < MAX_PER_ROUTE) {
            String text = expression != null && !expression.isBlank() ? expression.strip() : null;
            if (text != null && text.length() > 200) {
                text = text.substring(0, 200) + "...";
            }
            list.add(new DecisionPoint(scope.path(), type, text, Math.max(0, line)));
        }
    }
}
