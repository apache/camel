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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the big classes of the TUI from growing, as a coding agent reads a class or a method whole before it changes
 * it. A source file over {@value #MAX_FILE_LINES} lines, or a method over {@value #MAX_METHOD_LINES}, fails here unless
 * it is in {@link #OVER_THE_LIMITS}, and one in there fails when it grows past its size there. Put new code in a class
 * of its own (AGENTS.md) rather than raising a size; lower a size when a file or a method shrinks, and remove its line
 * once it is under the limit.
 */
class SourceSizeTest {

    private static final int MAX_FILE_LINES = 2000;
    private static final int MAX_METHOD_LINES = 150;
    private static final Path SOURCES = Path.of("src/main/java");

    /** The files and the methods (class.method) over the limits, and the most lines each may have. */
    private static final String OVER_THE_LIMITS = """
            AiPanel.java 3187
            AiPanel.runAgentLoop 159
            AiUsageStats.render 166
            CamelMonitor.java 2780
            DiagramDetailSupport.renderEipInfoPanel 159
            DiagramDetailSupport.renderRouteInfoPanel 154
            DiagramSupport.java 2460
            DiagramSupport.loadAllDiagramsInBackground 180
            DiagramSupport.loadHighlightedNativeDiagramInBackground 212
            EndpointsTab.renderContent 170
            HistoryTab.java 2727
            HistoryTab.renderDiagramInfoPanel 202
            LogTab.render 173
            MemoryLeakTab.java 2033
            NetworkTab.renderContent 162
            OllamaMonitor.toJson 206
            OverviewTab.buildOverviewTable 185
            OverviewTab.renderThroughputChart 158
            RoutesTab.renderProcessors 179
            SendMessagePopup.doSend 156
            SendMessagePopup.handleKeyEvent 220
            SettingsPopup.handleKeyEvent 160
            SourceEditAssist.java 2006
            SourceViewer.handleEditKeyEvent 231
            SourceViewer.java 3055
            SourceViewer.renderContent 207
            SourceViewer.renderEditMode 256
            TuiToolDefinitions.addInteractionTools 183
            TuiToolDefinitions.addStructuredDataTools 226
            XmlCompletionContext.at 152
            YamlRouteNodeScanner.scanLines 162
            """;

    @Test
    void noFileOrMethodGrowsOverTheLimits() throws IOException {
        Map<String, Integer> allowed = new TreeMap<>();
        OVER_THE_LIMITS.lines().map(String::strip).filter(l -> !l.isEmpty()).forEach(l -> {
            int space = l.lastIndexOf(' ');
            allowed.put(l.substring(0, space), Integer.parseInt(l.substring(space + 1)));
        });
        Map<String, Integer> sizes = new TreeMap<>();
        for (Path file : sources()) {
            int lines = (int) Files.readString(file).lines().count();
            if (lines > MAX_FILE_LINES) {
                sizes.put(file.getFileName().toString(), lines);
            }
        }
        sizes.putAll(longMethods());

        List<String> problems = new ArrayList<>();
        sizes.forEach((name, size) -> {
            Integer max = allowed.get(name);
            if (max == null) {
                problems.add(name + " has " + size + " lines, over the limit: split it");
            } else if (size > max) {
                problems.add(name + " grew to " + size + " lines, from at most " + max
                             + ": put the new code in a class of its own");
            }
        });
        allowed.keySet().stream().filter(name -> !sizes.containsKey(name))
                .forEach(name -> problems.add(name + " is under the limit now: remove its line from OVER_THE_LIMITS"));
        StringBuilder current = new StringBuilder();
        sizes.forEach((name, size) -> current.append(name).append(' ').append(size).append('\n'));
        assertThat(problems).as("Sizes over the limits now:%n%s", current).isEmpty();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** The methods over the limit by class.method, the longest of the overloads. */
    private static Map<String, Integer> longMethods() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Map<String, Integer> answer = new TreeMap<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, null, List.of("-proc:none"), null,
                    fm.getJavaFileObjectsFromPaths(sources()));
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                LineMap lineMap = unit.getLineMap();
                new TreeScanner<Void, String>() {
                    @Override
                    public Void visitClass(ClassTree node, String owner) {
                        String name = node.getSimpleName().toString();
                        // an anonymous class counts as part of the class around it
                        return super.visitClass(node, name.isEmpty() ? owner : owner == null ? name : owner);
                    }

                    @Override
                    public Void visitMethod(MethodTree node, String owner) {
                        long start = lineMap.getLineNumber(positions.getStartPosition(unit, node));
                        long end = lineMap.getLineNumber(positions.getEndPosition(unit, node));
                        int lines = (int) (end - start + 1);
                        if (lines > MAX_METHOD_LINES) {
                            answer.merge(owner + "." + node.getName(), lines, Math::max);
                        }
                        return super.visitMethod(node, owner);
                    }
                }.scan(unit, null);
            }
        }
        return answer;
    }
}
