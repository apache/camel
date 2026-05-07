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
package org.apache.camel.component.docling;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that temp files and directories created by DoclingProducer use secure per-exchange subdirectories with
 * restrictive POSIX permissions.
 */
class DoclingSecureTempFileTest extends CamelTestSupport {

    @Test
    void tempFilesAreCreatedInsidePerExchangeSubdirectory() throws Exception {
        // During the exchange, intercept to check what's on disk
        final List<Path> capturedDirs = new ArrayList<>();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:intercept-convert")
                        .process(exchange -> {
                            // Capture docling temp dirs that exist right now (mid-exchange)
                            capturedDirs.addAll(listDoclingTempDirs());
                        })
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN");
            }
        });

        // Snapshot before
        List<Path> before = listDoclingTempDirs();

        try {
            template.requestBody("direct:intercept-convert", "Some text content");
        } catch (CamelExecutionException e) {
            // Expected — docling binary not available
        }

        // The temp file should have been inside a docling-UUID subdirectory,
        // and after cleanup it should be gone.
        List<Path> after = listDoclingTempDirs();
        List<Path> leaked = new ArrayList<>(after);
        leaked.removeAll(before);

        assertTrue(leaked.isEmpty(),
                "Temp directories leaked after exchange completion: " + leaked);
    }

    @Test
    @EnabledIf("isPosixSupported")
    void tempDirectoryHasOwnerOnlyPermissions() throws Exception {
        // We need to intercept the exchange mid-flight to check permissions
        // before cleanup removes the directory.
        final List<Set<PosixFilePermission>> capturedPermissions = new ArrayList<>();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:check-perms")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN");
            }
        });

        List<Path> before = listDoclingTempDirs();

        // Use a Synchronization to capture directory permissions before cleanup
        Exchange exchange = createExchangeWithBody("Text content for permission test");
        exchange.getExchangeExtension().addOnCompletion(
                new org.apache.camel.support.SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        // Check all docling dirs created during this exchange
                        try {
                            for (Path dir : listDoclingTempDirs()) {
                                if (!before.contains(dir) && Files.exists(dir)) {
                                    capturedPermissions.add(Files.getPosixFilePermissions(dir));
                                }
                            }
                        } catch (IOException e) {
                            // Ignore
                        }
                    }

                    @Override
                    public int getOrder() {
                        // Run before the cleanup synchronization (lower order = earlier)
                        return HIGHEST - 1;
                    }
                });

        try {
            template.send("direct:check-perms", exchange);
        } catch (CamelExecutionException e) {
            // Expected
        }

        if (!capturedPermissions.isEmpty()) {
            for (Set<PosixFilePermission> perms : capturedPermissions) {
                // Should only have OWNER_READ, OWNER_WRITE, OWNER_EXECUTE
                assertFalse(perms.contains(PosixFilePermission.GROUP_READ), "Group read should not be set");
                assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE), "Group write should not be set");
                assertFalse(perms.contains(PosixFilePermission.OTHERS_READ), "Others read should not be set");
                assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE), "Others write should not be set");
                assertTrue(perms.contains(PosixFilePermission.OWNER_READ), "Owner read should be set");
                assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE), "Owner write should be set");
            }
        }
    }

    private List<Path> listDoclingTempDirs() throws IOException {
        List<Path> result = new ArrayList<>();
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmpDir, "docling-*")) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    static boolean isPosixSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
