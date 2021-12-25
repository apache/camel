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
package org.apache.camel.dsl.jsh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Jsh {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jsh.class);
    private static final ThreadLocal<Map<String, Object>> BINDINGS = ThreadLocal.withInitial(ConcurrentHashMap::new);

    private Jsh() {
        // no-op
    }

    public static List<String> compile(JShell jshell, String script) throws ScriptException {
        List<String> snippets = new ArrayList<>();

        while (!script.isEmpty()) {
            SourceCodeAnalysis.CompletionInfo ci = jshell.sourceCodeAnalysis().analyzeCompletion(script);
            if (!ci.completeness().isComplete()) {
                throw new ScriptException("Incomplete script:\n" + script);
            }

            snippets.add(ci.source());
            script = ci.remaining();
        }

        return snippets;
    }

    public static void setBinding(JShell jshell, String name, Object value) throws ScriptException {
        ObjectHelper.notNull(jshell, "jshell");
        ObjectHelper.notNull(name, "name");
        ObjectHelper.notNull(value, "value");

        setBinding(jshell, name, value, value.getClass());
    }

    public static <T> void setBinding(JShell jshell, String name, T value, Class<? extends T> type) throws ScriptException {
        ObjectHelper.notNull(jshell, "jshell");
        ObjectHelper.notNull(name, "name");
        ObjectHelper.notNull(value, "value");
        ObjectHelper.notNull(type, "type");

        setBinding(name, value);

        // As JShell leverages LocalExecutionControl as execution engine and thus JShell
        // runs in the current process it is possible to access to local classes, we use
        // such capability to inject bindings as variables.
        String snippet = String.format(
                "var %s = %s.getBinding(\"%s\", %s.class);",
                name,
                Jsh.class.getName(),
                name,
                type.getName());

        eval(jshell, snippet);
    }

    public static Object getBinding(String name) {
        return BINDINGS.get().get(name);
    }

    public static <T> T getBinding(String name, Class<T> type) {
        Object answer = BINDINGS.get().get(name);
        return answer != null ? type.cast(answer) : null;
    }

    public static void setBinding(String name, Object value) {
        BINDINGS.get().put(name, value);
    }

    public static void clearBindings() {
        BINDINGS.get().clear();
    }

    public static void eval(JShell jshell, String snippet) throws ScriptException {
        LOGGER.debug("Evaluating {}", snippet);

        List<SnippetEvent> events = jshell.eval(snippet);

        for (SnippetEvent event : events) {
            if (event.exception() != null) {
                throw new ScriptException(event.exception());
            }
            if (event.status() != Snippet.Status.VALID) {
                throw new ScriptException("Error evaluating snippet:\n" + event.snippet().source());
            }
        }
    }

    public static ExecutionControlProvider wrapExecutionControl(String name, ExecutionControl delegate) {
        return new ExecutionControlProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) throws Throwable {
                return delegate;
            }
        };
    }
}
