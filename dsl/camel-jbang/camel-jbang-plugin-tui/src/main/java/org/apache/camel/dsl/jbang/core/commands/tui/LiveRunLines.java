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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The live run data of a source file: what the processors of a running integration on each line of it did, read from
 * the routes and processors of the status (their source location, file:line), for the Source tab to show at the end of
 * the lines - a heat map of the source while it runs.
 */
final class LiveRunLines {

    private LiveRunLines() {
    }

    /**
     * @param  routes   the routes of the running integration, with their processors
     * @param  filePath the file shown
     * @return          per 0-based line of the file, the exchanges, failures and mean time of the processors on it
     */
    static Map<Integer, SourceViewer.LiveLine> of(List<RouteInfo> routes, String filePath) {
        Map<Integer, long[]> sums = new HashMap<>();
        String name = nameOf(filePath);
        if (routes == null || name == null) {
            return Map.of();
        }
        for (RouteInfo r : routes) {
            // the route itself: its input, on the line of from
            add(sums, name, r.source, r.total, r.failed, r.meanTime);
            for (ProcessorInfo p : r.processors) {
                add(sums, name, p.source, p.total, p.failed, p.meanTime);
            }
        }
        Map<Integer, SourceViewer.LiveLine> answer = new HashMap<>();
        sums.forEach((line, s) -> answer.put(line, new SourceViewer.LiveLine(s[0], s[1], s[0] > 0 ? s[2] / s[0] : 0)));
        return answer;
    }

    /** Adds what a processor did to its line: totals and failures summed, the mean time weighted by the exchanges. */
    private static void add(Map<Integer, long[]> sums, String name, String source, long total, long failed, long mean) {
        int line = lineOf(source, name);
        if (line < 0) {
            return;
        }
        long[] s = sums.computeIfAbsent(line, k -> new long[3]);
        s[0] += total;
        s[1] += failed;
        s[2] += Math.max(0, mean) * total;
    }

    /**
     * The 0-based line of a source location (file:line) in the file of the given name, or -1 when the location is of
     * another file or has no line.
     */
    static int lineOf(String source, String name) {
        if (source == null || name == null) {
            return -1;
        }
        int colon = source.lastIndexOf(':');
        if (colon <= 0 || !name.equals(nameOf(source.substring(0, colon)))) {
            return -1;
        }
        int line;
        try {
            line = Integer.parseInt(source.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        return line > 0 ? line - 1 : -1;
    }

    /** The file name of a location: file:/work/OrderRoute.java, classpath:OrderRoute.java and OrderRoute.java alike. */
    static String nameOf(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String s = location.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        s = slash >= 0 ? s.substring(slash + 1) : s;
        int scheme = s.lastIndexOf(':');
        return scheme >= 0 ? s.substring(scheme + 1) : s;
    }
}
