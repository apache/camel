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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "groovy", displayName = "Groovy", description = "Groovy Language")
public class GroovyDevConsole extends AbstractDevConsole {

    public record CompilerInfo(
            @Metadata(description = "The script file name pattern") String scriptPattern,
            @Metadata(description = "Number of scripts compiled") int compiledCounter,
            @Metadata(description = "Number of scripts pre-loaded") int preloadedCounter,
            @Metadata(description = "Number of compiled classes") int classesSize,
            @Metadata(description = "Total compile time in milliseconds") long compiledTime,
            @Metadata(description = "Whether re-compilation is enabled") boolean recompileEnabled,
            @Metadata(description = "Epoch time in milliseconds of the last compilation") long lastCompilationTimestamp,
            @Metadata(description = "The work directory (only present when configured)") String workDir,
            @Metadata(description = "The compiled class names (only present when there are any)") List<String> classes) {
    }

    public record Response(
            @Metadata(description = "The Groovy compiler information (only present when the compiler is active)") CompilerInfo compiler) {
    }

    public GroovyDevConsole() {
        super("camel", "groovy", "Groovy", "Groovy Language");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        DefaultGroovyScriptCompiler compiler = getCamelContext().hasService(DefaultGroovyScriptCompiler.class);
        if (compiler != null) {
            sb.append(String.format("    Script Pattern: %s", compiler.getScriptPattern()));
            sb.append(String.format("%n    Pre-loaded Counter: %s", compiler.getPreloadedCounter()));
            sb.append(String.format("%n    Compile Counter: %s", compiler.getCompileCounter()));
            sb.append(String.format("%n    Compile Time: %s (ms)", compiler.getCompileTime()));
            long last = compiler.getLastCompilationTimestamp();
            if (last != 0) {
                sb.append(String.format("%n    Compile Ago: %s",
                        TimeUtils.printSince(compiler.getLastCompilationTimestamp())));
            }
            sb.append(String.format("%n    Re-compile Enabled: %b", compiler.isRecompileEnabled()));
            if (compiler.getWorkDir() != null) {
                sb.append(String.format("%n    Work Directory: %s", compiler.getWorkDir()));
            }
            sb.append(String.format("%n    Classes: (%d)", compiler.getClassesSize()));
            for (String name : compiler.compiledClassNames()) {
                sb.append(String.format("%n        %s", name));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        CompilerInfo compilerInfo = null;

        DefaultGroovyScriptCompiler compiler = getCamelContext().hasService(DefaultGroovyScriptCompiler.class);
        if (compiler != null) {
            List<String> classes = new ArrayList<>(compiler.compiledClassNames());
            compilerInfo = new CompilerInfo(
                    compiler.getScriptPattern(), compiler.getCompileCounter(), compiler.getPreloadedCounter(),
                    compiler.getClassesSize(), compiler.getCompileTime(), compiler.isRecompileEnabled(),
                    compiler.getLastCompilationTimestamp(), compiler.getWorkDir(), classes.isEmpty() ? null : classes);
        }

        Response response = new Response(compilerInfo);
        return JsonRecordSupport.toJsonObject(response);
    }
}
