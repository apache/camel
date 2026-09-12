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
package org.apache.camel.language.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for the GraalJS {@link Engine} and {@link Context} used by the JavaScript language.
 */
public final class JavaScriptHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JavaScriptHelper.class);

    private JavaScriptHelper() {
    }

    /**
     * Creates the {@link Engine} shared by all contexts of one {@link JavaScriptLanguage} instance. Sharing the engine
     * lets GraalJS reuse parsed sources and compiled code across the per-evaluation contexts instead of re-parsing
     * every script and allocating a private engine on each evaluation.
     */
    public static Engine newEngine() {
        Engine engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        if (Engine.supportsCompilation()) {
            LOG.debug("Created GraalJS engine ({}) with JIT compilation support", engine.getImplementationName());
        } else {
            LOG.info("Created GraalJS engine in interpreter-only mode ({}); scripts are not JIT compiled."
                     + " Run on a GraalVM JDK or add the Truffle compiler runtime to the module path for better performance",
                    engine.getImplementationName());
        }
        return engine;
    }

    /**
     * Builds a per-evaluation context backed by the given shared engine. Each context is isolated from the others; only
     * the parsed and compiled code is shared through the engine.
     */
    public static Context newContext(Engine engine) {
        return configure(Context.newBuilder("js").engine(engine)).build();
    }

    /**
     * Builds a context with its own private engine.
     *
     * @deprecated use {@link #newContext(Engine)} with a shared engine so parsed and compiled code is reused
     */
    @Deprecated(since = "4.23.0")
    public static Context newContext() {
        return configure(Context.newBuilder("js"))
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    private static Context.Builder configure(Context.Builder builder) {
        return builder
                .allowIO(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowPolyglotAccess(PolyglotAccess.NONE);
    }
}
