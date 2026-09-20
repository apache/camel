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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks on the order of the top-level entries of a YAML route file. The schema accepts the entries in any order; the
 * runtime does not: an onException, onCompletion or intercept written after a route fails at startup with "onException
 * must be defined before any routes in the RouteBuilder", a Java DSL sentence for a YAML author (CAMEL-24846). The
 * check names the line, the entry, and where it goes.
 */
public final class StructureChecks {

    /** A top-level list item: "- route:", "- onException:" (indent 0). */
    private static final Pattern TOP_LEVEL_ENTRY = Pattern.compile("^- ([A-Za-z][A-Za-z0-9]*)\\s*:");

    /** The entries the runtime requires before the first route. */
    private static final Set<String> BEFORE_ROUTES
            = Set.of("onException", "onCompletion", "intercept", "interceptFrom", "interceptSendToEndpoint");

    /** The entries that are a route. */
    private static final Set<String> ROUTES = Set.of("route", "from");

    private StructureChecks() {
    }

    /**
     * The entries written after the first route that the runtime requires before it, one message per entry with the
     * line of the entry and the line of the first route.
     */
    public static List<String> validateTopLevelOrder(String content) {
        List<String> errors = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return errors;
        }
        String[] lines = content.split("\n", -1);
        int firstRoute = -1;
        for (int i = 0; i < lines.length; i++) {
            Matcher m = TOP_LEVEL_ENTRY.matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            String entry = m.group(1);
            if (ROUTES.contains(entry)) {
                if (firstRoute < 0) {
                    firstRoute = i;
                }
            } else if (firstRoute >= 0 && BEFORE_ROUTES.contains(entry)) {
                errors.add("Line " + (i + 1) + ": " + entry + " must come before the routes: move this entry above the"
                           + " first - " + (lines[firstRoute].trim().startsWith("- from") ? "from" : "route")
                           + ": (line " + (firstRoute + 1) + "); the runtime refuses it at startup ('" + entry
                           + " must be defined before any routes')");
            }
        }
        return errors;
    }
}
