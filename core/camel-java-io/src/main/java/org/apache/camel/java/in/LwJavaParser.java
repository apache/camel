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
import java.util.Map;

/**
 * Reads the routes of a Java DSL source into the Camel model without compiling it (CAMEL-25148), the reverse of
 * {@link org.apache.camel.java.LwModelToJavaDumper}.
 * <p/>
 * It reads the source as text: the imports, the constants of the class, and the statements of {@code configure()} (or
 * the statements of a snippet), then replays each chain of calls against Camel's own DSL to build the model. Nothing of
 * the project is compiled, loaded or run, so it needs no classpath of the project and takes milliseconds.
 * <p/>
 * What it cannot work out is reported in {@link JavaParseResult#unresolved()}, and a value it could not work out is
 * kept in the model as {@code ?{source text}}: values computed at runtime or in helper methods, routes built in loops,
 * lambdas and processors, the endpoint DSL.
 */
public final class LwJavaParser {

    /** Marks a value in the model the parser could not work out: {@code ?{source text}}. */
    public static final String UNRESOLVED_PREFIX = "?{";

    private EndpointDslResolver endpointDsl;
    private ConstantResolver constantResolver;

    /** Resolves constants of classes the parser cannot see: component header constants, other project sources. */
    public LwJavaParser setConstantResolver(ConstantResolver resolver) {
        this.constantResolver = resolver;
        return this;
    }

    /**
     * The constants a Java source declares that are data (Strings, numbers, booleans), by name, worked out as the
     * parser works them out: for a resolver that reads the other source files of a project. Empty when it cannot be
     * read.
     */
    public static Map<String, Object> constants(String source) {
        if (source == null || source.length() > MAX_SOURCE_LENGTH) {
            return Map.of();
        }
        try {
            return new ChainReplayer(JavaChainParser.parse(source)).constantValues();
        } catch (RuntimeException | StackOverflowError e) {
            return Map.of();
        }
    }

    /**
     * Resolves endpoint DSL calls with the given resolver, for every call that is not Camel's DSL. Without one, the
     * naming rules of the endpoint DSL ({@link EndpointDslResolver#NAMING}) are used, and only for sources that use the
     * endpoint DSL (they extend EndpointRouteBuilder or import from org.apache.camel.builder.endpoint): a resolver with
     * the catalog can tell a component from a helper method, the naming rules cannot.
     */
    public LwJavaParser setEndpointDslResolver(EndpointDslResolver resolver) {
        this.endpointDsl = resolver;
        return this;
    }

    /** The largest source it reads, in characters: far above any route class, low enough to bound the work. */
    public static final int MAX_SOURCE_LENGTH = 2 * 1024 * 1024;

    /**
     * Parses a Java source: a RouteBuilder class, or statements such as a documentation snippet. It never throws: a
     * source it cannot read gives an empty model with the reason in {@link JavaParseResult#unresolved()}.
     */
    public JavaParseResult parse(String source) {
        if (source == null || source.length() > MAX_SOURCE_LENGTH) {
            return failed("a source over " + MAX_SOURCE_LENGTH + " characters is not read");
        }
        try {
            return new ChainReplayer(JavaChainParser.parse(source), endpointDsl, constantResolver).replay();
        } catch (RuntimeException | StackOverflowError e) {
            return failed("the parser failed: " + e);
        }
    }

    private static JavaParseResult failed(String reason) {
        ChainReplayer.ReplayBuilder empty = new ChainReplayer.ReplayBuilder();
        return new JavaParseResult(
                empty.getRouteCollection(), empty.getRestCollection(), empty.getRouteTemplateCollection(),
                empty.getRouteConfigurationCollection(), List.of(new JavaParseResult.Unresolved(1, "", reason)));
    }
}
