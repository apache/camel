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
package org.apache.camel.language.python3;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;

/**
 * Factory for GraalPy {@link Engine} and {@link Context} used by the Python 3 language.
 */
public final class Python3Helper {

    private Python3Helper() {
    }

    /**
     * Host access that lets Python index Java {@link java.util.Map} and {@link java.util.List} values (headers,
     * properties, body collections) without allowing arbitrary host method invocation. Does not use
     * {@link HostAccess#ALL} or {@code allowAllAccess}.
     */
    public static HostAccess defaultHostAccess() {
        return HostAccess.newBuilder()
                .allowMapAccess(true)
                .allowListAccess(true)
                .allowArrayAccess(true)
                .allowIterableAccess(true)
                .allowIteratorAccess(true)
                .build();
    }

    public static Engine newEngine() {
        return Engine.newBuilder("python")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    /**
     * Builds a per-eval context. Intentionally does not call {@code allowAllAccess}, {@code allowHostClassLookup},
     * {@code allowIO}, or {@code allowCreateProcess}. {@code HostAccess.ALL} (trusted mode) only unlocks public members
     * of already-bound host objects; it is not a sandbox and does not grant class lookup or IO.
     */
    public static Context newContext(Engine engine, HostAccess hostAccess) {
        return Context.newBuilder("python")
                .engine(engine)
                .allowHostAccess(hostAccess)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .option("python.PosixModuleBackend", "java")
                .build();
    }
}
