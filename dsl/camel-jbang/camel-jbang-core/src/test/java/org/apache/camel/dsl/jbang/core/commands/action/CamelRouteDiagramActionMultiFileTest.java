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

import java.nio.file.Files;
import java.nio.file.Path;
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
 * Covers the two pieces of CamelRouteDiagramAction's multi-file source support that don't require spawning a real
 * transient Camel process (that spawn path, doCallSource -> Run.runTransform, is out of scope for unit tests, same as
 * CamelRouteStructureActionTest documents): (1) upfront validation that every given file exists before anything is
 * booted, and (2) merging routes from several per-resource structure JSON files, which is the piece CAMEL-23850 needs
 * to render a diagram spanning more than one source file.
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
        // must be skipped: not a route-structure file
        Files.writeString(tempDir.resolve("route-topology.json"), """
                {"nodes":[],"edges":[]}
                """);

        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain());
        List<RouteInfo> routes = command.readRoutesFromFolder(tempDir);

        assertEquals(2, routes.size(), "should merge routes from both structure files, but not route-topology.json");
        assertTrue(routes.stream().anyMatch(r -> "route1".equals(r.routeId)));
        assertTrue(routes.stream().anyMatch(r -> "route2".equals(r.routeId)));
    }
}
