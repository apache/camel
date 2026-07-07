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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pieces of CamelRouteDiagramAction's multi-file source support that don't require spawning a real transient
 * Camel process (that spawn path, doCallSource -> Run.runTransform, is out of scope for unit tests, same as
 * CamelRouteStructureActionTest documents): (1) upfront validation that every given file exists before anything is
 * booted, (2) merging routes from several per-resource structure JSON files, which is needed to render a diagram that
 * spans more than one source file, and (3) waiting for the whole batch of dumped files to be complete (rather than
 * returning as soon as any single file appears) before merging.
 */
class CamelRouteDiagramActionMultiFileTest {

    @Test
    void testDoCallSourceReturnsNullWhenAnyFileIsMissing(@TempDir Path tempDir) throws Exception {
        Path existing = tempDir.resolve("route1.yaml");
        Files.writeString(existing, "- from:\n    uri: timer:tick\n");

        StringPrinter printer = new StringPrinter();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));

        List<RouteInfo> routes = command.doCallSource(List.of(existing.toString(), "does-not-exist.yaml"));

        assertNull(routes, "should report an error and not attempt to boot Camel when a file is missing");
        assertTrue(printer.getOutput().contains("File does not exist: does-not-exist.yaml"),
                "should report the missing file, was: " + printer.getOutput());
    }

    @Test
    void testReadRoutesFromFolderMergesMultipleStructureFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("route1.json"), """
                {"routes":[{"routeId":"route1","code":[{"type":"from","code":"timer:tick","level":0}]}]}
                """);
        Files.writeString(tempDir.resolve("route2.json"), """
                {"routes":[{"routeId":"route2","code":[{"type":"from","code":"direct:linked","level":0}]}]}
                """);
        // must be skipped: not a route-structure file, even though it has a "routes" array that would otherwise
        // be parsed as one (this is what actually distinguishes the filter from a no-op)
        Files.writeString(tempDir.resolve("route-topology.json"), """
                {"nodes":[],"edges":[],"routes":[{"routeId":"bogus","code":[]}]}
                """);

        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain());
        List<RouteInfo> routes = command.readRoutesFromFolder(tempDir, Instant.now().minusSeconds(60));

        assertEquals(2, routes.size(), "should merge routes from both structure files, but not route-topology.json");
        assertTrue(routes.stream().anyMatch(r -> "route1".equals(r.routeId)));
        assertTrue(routes.stream().anyMatch(r -> "route2".equals(r.routeId)));
        assertTrue(routes.stream().noneMatch(r -> "bogus".equals(r.routeId)),
                "route-topology.json must be excluded from the merge even though it has a routes array");
    }

    @Test
    void testReadRoutesFromFolderWaitsForCompleteBatchBeforeMerging(@TempDir Path tempDir) throws Exception {
        // route1.json lands immediately, as if the dump of a multi-file batch is still in progress
        Instant bootStart = Instant.now();
        Files.writeString(tempDir.resolve("route1.json"), """
                {"routes":[{"routeId":"route1","code":[{"type":"from","code":"timer:tick","level":0}]}]}
                """);

        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain());

        // route2.json and the route-topology.json completion marker (always written last by
        // DefaultDumpRoutesStrategy) land shortly after; if readRoutesFromFolder returned as soon as any file
        // appeared (the pre-fix behavior), it would race ahead and merge only route1, silently dropping route2
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(300);
                Files.writeString(tempDir.resolve("route2.json"), """
                        {"routes":[{"routeId":"route2","code":[{"type":"from","code":"direct:linked","level":0}]}]}
                        """);
                Files.writeString(tempDir.resolve("route-topology.json"), """
                        {"nodes":[],"edges":[]}
                        """);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-delayed-dump-writer");
        writer.start();
        try {
            List<RouteInfo> routes = command.readRoutesFromFolder(tempDir, bootStart);
            assertEquals(2, routes.size(),
                    "must wait for the route-topology.json completion marker before merging, not return as soon "
                                           + "as the first file appears");
        } finally {
            writer.join();
        }
    }

    @Test
    void testReadRoutesFromFolderIgnoresStaleTopologyMarker(@TempDir Path tempDir) throws Exception {
        // simulate a route-topology.json left behind by a previous --watch iteration whose cleanup silently failed
        Path topologyMarker = tempDir.resolve("route-topology.json");
        Files.writeString(topologyMarker, "{\"nodes\":[],\"edges\":[]}");
        Files.setLastModifiedTime(topologyMarker, FileTime.from(Instant.now().minusSeconds(30)));

        Instant bootStart = Instant.now();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain());

        // this iteration's own dump lands shortly after, rewriting the marker with a fresh timestamp
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(300);
                Files.writeString(tempDir.resolve("route1.json"), """
                        {"routes":[{"routeId":"route1","code":[{"type":"from","code":"timer:tick","level":0}]}]}
                        """);
                Files.writeString(topologyMarker, "{\"nodes\":[],\"edges\":[]}");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-delayed-refresh-writer");
        writer.start();
        try {
            List<RouteInfo> routes = command.readRoutesFromFolder(tempDir, bootStart);
            assertEquals(1, routes.size(),
                    "must ignore the stale marker and wait for this iteration's own (freshly-written) dump");
        } finally {
            writer.join();
        }
    }
}
