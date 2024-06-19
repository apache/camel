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
package org.apache.camel.updates.camel40.xml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.updates.AbstractCamelXmlVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

/**
 * <p>
 * <a:href link="https://camel.apache.org/manual/camel-4-migration-guide.html#_circuitbreaker_eip" >Camel Migration
 * guide</a:href>
 * </p>
 * The following options in camel-resilience4j was mistakenly not defined as attributes:
 * <ul>
 * <li>bulkheadEnabled</li>
 * <li>bulkheadMaxConcurrentCalls</li>
 * <li>bulkheadMaxWaitDuration</li>
 * <li>timeoutEnabled</li>
 * <li>timeoutExecutorService</li>
 * <li>timeoutDuration</li>
 * <li>timeoutCancelRunningFuture</li>
 * </ul>
 *
 * These options were not exposed in YAML DSL, and in XML DSL you need to migrate from:
 *
 * <pre>
 * &lt;circuitBreaker&gt;
 *     &lt;resilience4jConfiguration&gt;
 *         &lt;timeoutEnabled&gt;true&lt;/timeoutEnabled&gt;
 *         &lt;timeoutDuration&gt;2000&lt;/timeoutDuration&gt;
 *     &lt;/resilience4jConfiguration&gt;
 * ...
 * &lt;/circuitBreaker&gt;
 * &lt;/pre>
 *
 * To use attributes instead:
 *
 * <pre>
 * &lt;circuitBreaker&gt;
 *     &lt;resilience4jConfiguration timeoutEnabled="true" timeoutDuration="2000"/&gt;
 * ...
 * &lt;/circuitBreaker&gt;
 * </pre>
 */
public class CircuitBreakerXmlDslRecipe extends Recipe {

    private final static String RESILIENCE4J_XPATH = "*/circuitBreaker/resilience4jConfiguration";
    private static final XPathMatcher RESILIENCE4J_MATCHER = new XPathMatcher(RESILIENCE4J_XPATH);

    private static final Map<String, XPathMatcher> ATTRIBUTE_MATCHERS = Stream.of(
            "bulkheadEnabled",
            "bulkheadMaxConcurrentCalls",
            "bulkheadMaxWaitDuration",
            "timeoutEnabled",
            "timeoutExecutorService",
            "timeoutDuration",
            "timeoutCancelRunningFuture")
            .collect(Collectors.toMap(
                    k -> k,
                    k -> new XPathMatcher(RESILIENCE4J_XPATH + "/" + k),
                    (v1, v2) -> v1 //conflict can not happen
            ));

    @Override
    public String getDisplayName() {
        return "Camel XMl DSL Circuit Breaker changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel XML DSL Circuit Breaker migration from version 3.20 or higher to 4.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AbstractCamelXmlVisitor() {

            @Override
            public Xml.Tag doVisitTag(final Xml.Tag tag, final ExecutionContext ctx) {
                Xml.Tag t = super.doVisitTag(tag, ctx);

                if (RESILIENCE4J_MATCHER.matches(getCursor())) {
                    Map<String, String> values = ctx.pollMessage(RESILIENCE4J_XPATH);

                    if (values != null && !values.isEmpty()) {
                        //create list of values
                        List<Xml.Attribute> toAdd = values.entrySet().stream()
                                .map(e -> autoFormat(new Xml.Attribute(
                                        Tree.randomId(), "", Markers.EMPTY,
                                        new Xml.Ident(Tree.randomId(), "", Markers.EMPTY, e.getKey()),
                                        "",
                                        autoFormat(new Xml.Attribute.Value(
                                                Tree.randomId(), "", Markers.EMPTY,
                                                Xml.Attribute.Value.Quote.Double,
                                                e.getValue()), ctx)),
                                        ctx))
                                .collect(Collectors.toList());

                        return t.withAttributes(ListUtils.concatAll(t.getAttributes(), toAdd));
                    }
                }

                for (Map.Entry<String, XPathMatcher> entry : ATTRIBUTE_MATCHERS.entrySet()) {
                    if (entry.getValue().matches(getCursor())) {
                        if (t.getValue().isPresent() && !t.getValue().get().isEmpty()) {
                            Map<String, String> values = ctx.getMessage(RESILIENCE4J_XPATH, new LinkedHashMap<>());
                            values.put(entry.getKey(), t.getValue().get());
                            ctx.putMessage(RESILIENCE4J_XPATH, values);
                        }
                        //skip tag
                        return null;
                    }
                }

                return t;
            }
        };
    }
}
