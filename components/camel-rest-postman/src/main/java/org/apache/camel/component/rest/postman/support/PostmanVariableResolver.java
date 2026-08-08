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
package org.apache.camel.component.rest.postman.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@code {{name}}} placeholders that Postman collections use in place of environment values.
 * <p>
 * Scopes are layered, with the innermost winning:
 * <ol>
 * <li>collection level {@code variable}</li>
 * <li>folder level {@code variable}, outermost folder first</li>
 * <li>the request's own {@code url.variable}</li>
 * <li>the endpoint's {@code variables} option</li>
 * <li>Camel property placeholders</li>
 * </ol>
 * The first four are merged into the map handed to the constructor; property placeholders are consulted last, when a
 * name is otherwise unknown, so that an operator can override anything the collection ships with.
 */
public final class PostmanVariableResolver {

    /**
     * How many times substitution will re-scan its own output. A collection can define {@code a = {{b}}} and {@code b =
     * {{a}}}, so the rewriting has to be bounded or it never terminates.
     */
    private static final int MAX_DEPTH = 5;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    private static final Logger LOG = LoggerFactory.getLogger(PostmanVariableResolver.class);

    private final Map<String, String> variables;
    private final CamelContext camelContext;
    private final boolean failOnUnresolved;

    public PostmanVariableResolver(Map<String, String> variables, CamelContext camelContext, boolean failOnUnresolved) {
        this.variables = new LinkedHashMap<>(variables);
        this.camelContext = camelContext;
        this.failOnUnresolved = failOnUnresolved;
    }

    /**
     * Substitutes every known placeholder in the text.
     * <p>
     * Unknown placeholders are left verbatim, so that a URL such as {@code {{baseUrl}}/users} still reveals what was
     * missing when it later fails, unless {@code failOnUnresolvedVariable} was enabled.
     *
     * @param  text    the text to expand, may be {@code null}
     * @param  context what is being expanded, used in the failure message
     * @return         the expanded text, or {@code null} when the input was {@code null}
     */
    public String resolve(String text, String context) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }

        String current = text;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Matcher matcher = PLACEHOLDER.matcher(current);
            StringBuilder answer = new StringBuilder();
            boolean replaced = false;

            while (matcher.find()) {
                String name = matcher.group(1).trim();
                String value = lookup(name);
                if (value != null) {
                    matcher.appendReplacement(answer, Matcher.quoteReplacement(value));
                    replaced = true;
                } else {
                    matcher.appendReplacement(answer, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(answer);
            current = answer.toString();

            if (!replaced) {
                break;
            }
            if (depth == MAX_DEPTH - 1 && PLACEHOLDER.matcher(current).find()) {
                LOG.warn("Postman variable substitution in {} stopped after {} passes, which usually means two"
                         + " variables reference each other. Remaining placeholders are left as-is: {}",
                        context, MAX_DEPTH, current);
            }
        }

        if (failOnUnresolved) {
            Matcher remaining = PLACEHOLDER.matcher(current);
            if (remaining.find()) {
                throw new IllegalArgumentException(
                        "Postman variable {{" + remaining.group(1).trim() + "}} used in " + context
                                                   + " cannot be resolved. Define it in the collection, or supply it"
                                                   + " with the variables option, or set failOnUnresolvedVariable=false.");
            }
        }
        return current;
    }

    private String lookup(String name) {
        String value = variables.get(name);
        if (value != null) {
            return value;
        }
        if (camelContext != null && isSafeToResolveAsProperty(name)) {
            try {
                // resolvePropertyPlaceholders throws when the key is unknown, which here just means "not ours"
                return camelContext.resolvePropertyPlaceholders("{{" + name + "}}");
            } catch (Exception e) {
                LOG.trace("Postman variable {} is not a Camel property placeholder either", name, e);
            }
        }
        return null;
    }

    /**
     * Whether a placeholder name read out of a collection may be handed to Camel's property resolver.
     * <p>
     * Camel's placeholder <em>functions</em> are all written {@code prefix:argument} - {@code env:}, {@code sys:},
     * {@code bean:} and the vault functions among them. A collection is route-author configuration, but a cloud-hosted
     * one is editable by anyone with access to the Postman workspace, so letting its content name those functions would
     * turn "read the collection" into "read this environment variable and put it in an outgoing request". Plain names
     * are resolved as before, so an operator can still override any variable through properties; only the function
     * syntax is refused.
     */
    private static boolean isSafeToResolveAsProperty(String name) {
        if (name.indexOf(':') < 0) {
            return true;
        }
        LOG.debug("Postman variable {} is not resolved from Camel properties because it uses the prefix:value syntax"
                  + " of a property placeholder function. Supply it with the variables option instead.",
                name);
        return false;
    }

    /**
     * The merged variable scope, for diagnostics.
     */
    public Map<String, String> getVariables() {
        return Map.copyOf(variables);
    }
}
