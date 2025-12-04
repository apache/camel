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

package org.apache.camel.language.groovy;

import java.util.Map;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "groovy", displayName = "Groovy", description = "Groovy Language")
public class GroovyDevConsole extends AbstractDevConsole {

    public GroovyDevConsole() {
        super("camel", "groovy", "Groovy", "Groovy Language");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        DefaultGroovyScriptCompiler compiler = getCamelContext().hasService(DefaultGroovyScriptCompiler.class);
        if (compiler != null) {
            sb.append(String.format("    Script Pattern: %s", compiler.getScriptPattern()));
            sb.append(String.format("\n    Pre-loaded Counter: %s", compiler.getPreloadedCounter()));
            sb.append(String.format("\n    Compile Counter: %s", compiler.getCompileCounter()));
            sb.append(String.format("\n    Compile Time: %s (ms)", compiler.getCompileTime()));
            long last = compiler.getLastCompilationTimestamp();
            if (last != 0) {
                sb.append(String.format(
                        "\n    Compile Ago: %s", TimeUtils.printSince(compiler.getLastCompilationTimestamp())));
            }
            sb.append(String.format("\n    Re-compile Enabled: %b", compiler.isRecompileEnabled()));
            if (compiler.getWorkDir() != null) {
                sb.append(String.format("\n    Work Directory: %s", compiler.getWorkDir()));
            }
            sb.append(String.format("\n    Classes: (%d)", compiler.getClassesSize()));
            for (String name : compiler.compiledClassNames()) {
                sb.append(String.format("\n        %s", name));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        DefaultGroovyScriptCompiler compiler = getCamelContext().hasService(DefaultGroovyScriptCompiler.class);
        if (compiler != null) {
            JsonObject jo = new JsonObject();
            jo.put("scriptPattern", compiler.getScriptPattern());
            jo.put("compiledCounter", compiler.getCompileCounter());
            jo.put("preloadedCounter", compiler.getPreloadedCounter());
            jo.put("classesSize", compiler.getClassesSize());
            jo.put("compiledTime", compiler.getCompileTime());
            jo.put("recompileEnabled", compiler.isRecompileEnabled());
            jo.put("lastCompilationTimestamp", compiler.getLastCompilationTimestamp());
            if (compiler.getWorkDir() != null) {
                jo.put("workDir", compiler.getWorkDir());
            }
            JsonArray arr = new JsonArray(compiler.compiledClassNames());
            if (!arr.isEmpty()) {
                jo.put("classes", arr);
            }
            root.put("compiler", jo);
        }

        return root;
    }
}
