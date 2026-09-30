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
package org.apache.camel.java.in;

import java.util.List;

import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;

/**
 * The model read from a Java DSL source by {@link LwJavaParser}, and what could not be read. A result with unresolved
 * parts is partial: the model has a marked placeholder ({@link LwJavaParser#UNRESOLVED_PREFIX}) where a value was not
 * known, or lacks a step that could not be built.
 *
 * @param routes              the routes
 * @param rests               the REST services
 * @param routeTemplates      the route templates
 * @param routeConfigurations the route configurations
 * @param unresolved          what was not understood, in source order
 */
public record JavaParseResult(
        RoutesDefinition routes, RestsDefinition rests, RouteTemplatesDefinition routeTemplates,
        RouteConfigurationsDefinition routeConfigurations, List<Unresolved> unresolved) {

    /**
     * A part of the source the parser could not turn into the model.
     *
     * @param line   the line in the source
     * @param text   the source text of the part
     * @param reason why, such as "a lambda" or "no such DSL method"
     */
    public record Unresolved(int line, String text, String reason) {
    }

    /**
     * Whether an unresolved part is a statement that sets up the CamelContext (components, beans) in
     * {@code configure()}: not a route, so no route is partial because of it.
     */
    public static boolean configuresTheContext(Unresolved u) {
        return ChainReplayer.CONFIGURES_THE_CONTEXT.equals(u.reason());
    }

    /** Whether everything was read: no placeholders, no missing steps. */
    public boolean isComplete() {
        return unresolved.isEmpty();
    }
}
