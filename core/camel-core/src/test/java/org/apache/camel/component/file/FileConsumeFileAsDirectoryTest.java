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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file endpoint whose directory is an existing regular file used to start and stay silent; it fails with a message
 * that says what to write (CAMEL-24839).
 */
public class FileConsumeFileAsDirectoryTest extends ContextTestSupport {

    private static final String TEST_FILE_NAME = "as-directory-" + UUID.randomUUID() + ".json";
    private final Path file = Path.of(TEST_FILE_NAME);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @AfterEach
    public void deleteFile() throws Exception {
        Files.deleteIfExists(file);
    }

    @Test
    public void testConsumeFromAFile() throws Exception {
        Files.writeString(file, "{}");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("file:" + TEST_FILE_NAME + "?initialDelay=0&delay=10&noop=true").to("mock:result");
            }
        });
        Exception e = assertThrows(Exception.class, () -> context.start());
        String msg = e.getMessage() + (e.getCause() != null ? e.getCause().getMessage() : "");
        assertTrue(msg.contains(TEST_FILE_NAME + " is a file, not a directory"), msg);
        assertTrue(msg.contains("file:.?fileName=" + TEST_FILE_NAME), msg);
    }
}
