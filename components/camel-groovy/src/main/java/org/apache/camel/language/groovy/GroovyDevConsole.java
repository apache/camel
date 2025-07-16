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
            sb.append(String.format("    Groovy Script Pattern: %s", compiler.getScriptPattern()));
            sb.append(String.format("\n    Compiled Size: %s", compiler.getCompileSize()));
            sb.append(String.format("\n    Compiled Classes: %s", compiler.getClassesSize()));
            sb.append(String.format("\n    Compiled Time: %s (ms)", compiler.getCompileTime()));
            sb.append(String.format("\n    Re-compile Enabled: %b", compiler.isRecompileEnabled()));
            if (compiler.getWorkDir() != null) {
                sb.append(String.format("\n    Work Directory: %s", compiler.getWorkDir()));
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
            jo.put("groovyScriptPattern", compiler.getScriptPattern());
            jo.put("compiledSize", compiler.getCompileSize());
            jo.put("compiledClasses", compiler.getClassesSize());
            jo.put("compiledTime", compiler.getCompileTime());
            jo.put("recompileEnabled", compiler.isRecompileEnabled());
            if (compiler.getWorkDir() != null) {
                jo.put("workDir", compiler.getWorkDir());
            }
            root.put("compiler", jo);
        }

        return root;
    }
}
