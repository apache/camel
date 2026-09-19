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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A consumer on the current directory (file:.) delivers its files: java.io.File.isHidden is true for "." because the
 * name starts with a dot, which must not make the directory a hidden one (CAMEL-24835).
 */
public class FileConsumeCurrentDirTest extends ContextTestSupport {

    private static final String TEST_FILE_NAME = "current-dir-" + UUID.randomUUID() + ".txt";
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
    public void testConsumeCurrentDir() throws Exception {
        Files.writeString(file, "Report 123");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("file:.?initialDelay=0&delay=10&noop=true&include=" + TEST_FILE_NAME)
                        .convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Report 123");
        mock.assertIsSatisfied();
    }

    @Test
    public void testConsumeCurrentDirWithSlash() throws Exception {
        Files.writeString(file, "Report 456");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("file:./?initialDelay=0&delay=10&noop=true&include=" + TEST_FILE_NAME)
                        .convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Report 456");
        mock.assertIsSatisfied();
    }
}
