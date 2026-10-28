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
import java.util.List;
import java.util.function.Function;

import org.apache.camel.util.json.JsonObject;

/**
 * The runtime failures of a running integration for fix with AI (Shift+F8 in the Source editor and the Errors tab): the
 * source line an error of the error registry happened on, found through the source location of its processor
 * (file:line, as the live run data of the Source tab), and the exception of it as one line.
 */
final class RuntimeFailures {

    private static final int MAX_MESSAGE = 300;

    private RuntimeFailures() {
    }

    /** The errors of an integration, read from its error file (written by the running integration); empty if none. */
    static List<ErrorInfo> load(String pid, Function<String, Path> errorFileResolver) {
        if (pid == null) {
            return List.of();
        }
        try {
            JsonObject root = TuiHelper.loadStatus(Long.parseLong(pid), errorFileResolver);
            return root != null ? StatusParser.parseErrors(root) : List.of();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /**
     * The source location (file:line) of the processor an error happened at, else of its route; null when not known.
     */
    static String sourceOf(List<RouteInfo> routes, ErrorInfo error) {
        if (routes == null || error == null || error.routeId == null) {
            return null;
        }
        for (RouteInfo r : routes) {
            if (!error.routeId.equals(r.routeId)) {
                continue;
            }
            if (error.nodeId != null) {
                for (ProcessorInfo p : r.processors) {
                    if (error.nodeId.equals(p.id) && p.source != null) {
                        return p.source;
                    }
                }
            }
            return r.source;
        }
        return null;
    }

    /** The exception of an error as one line: the simple class name and the first line of the message. */
    static String describe(ErrorInfo error) {
        String message = error.exceptionMessage;
        if (message != null) {
            int nl = message.indexOf('\n');
            message = (nl >= 0 ? message.substring(0, nl) : message).strip();
            if (message.length() > MAX_MESSAGE) {
                message = message.substring(0, MAX_MESSAGE) + "...";
            }
        }
        String type = error.exceptionType;
        if (type == null) {
            return message;
        }
        String simple = type.substring(type.lastIndexOf('.') + 1);
        return message != null && !message.isEmpty() ? simple + ": " + message : simple;
    }

    /**
     * The exception of the last error on a 0-based line of a file, or null when no error of the error registry is in
     * the file. A failure also counts on the lines of the route and the EIPs around the processor it happened at (the
     * from, a choice), so when no error is on the line itself, the last error in the file is given with its line.
     */
    static String lastFailure(List<RouteInfo> routes, List<ErrorInfo> errors, String filePath, int line) {
        String name = LiveRunLines.nameOf(filePath);
        ErrorInfo onLine = null;
        ErrorInfo inFile = null;
        int inFileLine = -1;
        for (ErrorInfo e : errors) {
            int at = LiveRunLines.lineOf(sourceOf(routes, e), name);
            if (at == line && (onLine == null || e.timestamp > onLine.timestamp)) {
                onLine = e;
            } else if (at >= 0 && (inFile == null || e.timestamp > inFile.timestamp)) {
                inFile = e;
                inFileLine = at;
            }
        }
        if (onLine != null) {
            return describe(onLine);
        }
        return inFile != null ? describe(inFile) + " (at line " + (inFileLine + 1) + ")" : null;
    }

    /**
     * The failure of an error for the AI: its exception, and how many times it repeated when it did.
     */
    static String failureOf(ErrorInfo error) {
        String answer = describe(error);
        if (answer == null) {
            answer = "the exchange failed";
        }
        return error.repeatCount > 1 ? answer + " (" + error.repeatCount + " times)" : answer;
    }

    /**
     * The file of a source location (file:line) in the source directory of the integration, or null when it is not
     * there.
     */
    static Path fileOf(Path sourceDir, String source) {
        if (sourceDir == null || source == null) {
            return null;
        }
        int colon = source.lastIndexOf(':');
        String name = LiveRunLines.nameOf(colon > 0 ? source.substring(0, colon) : source);
        if (name == null) {
            return null;
        }
        Path direct = sourceDir.resolve(name);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        // a route in a folder of the project (src/main/resources/camel and alike)
        try (var paths = Files.walk(sourceDir, 6)) {
            return paths.filter(p -> Files.isRegularFile(p) && name.equals(p.getFileName().toString()))
                    .filter(p -> {
                        String path = p.toString().replace('\\', '/');
                        return !path.contains("/target/") && !path.contains("/.camel-jbang");
                    })
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** The text of a 0-based line of a file, or empty when it cannot be read. */
    static String lineText(Path file, int line) {
        try {
            List<String> lines = Files.readAllLines(file);
            return line >= 0 && line < lines.size() ? lines.get(line) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
