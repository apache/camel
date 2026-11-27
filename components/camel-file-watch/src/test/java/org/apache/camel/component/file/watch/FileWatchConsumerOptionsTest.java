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
package org.apache.camel.component.file.watch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.methvin.watcher.hashing.FileHash;
import io.methvin.watcher.hashing.FileHasher;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The events option, the files a non-recursive watch hashes, and the defaults of an endpoint made without the
 * component.
 */
class FileWatchConsumerOptionsTest {

    @TempDir
    Path dir;

    @Test
    void eventsWithSpacesAndLowerCaseAreAccepted() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            FileWatchEndpoint endpoint = context.getEndpoint("file-watch:" + dir, FileWatchEndpoint.class);
            endpoint.setEvents(" create , Modify");
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(endpoint).to("mock:events");
                }
            });
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:events", MockEndpoint.class);
            mock.expectedMinimumMessageCount(1);
            mock.message(0).header(FileWatchConstants.EVENT_TYPE_HEADER).isEqualTo("CREATE");
            Files.writeString(dir.resolve("created.txt"), "camel");

            mock.assertIsSatisfied();
        }
    }

    @Test
    void anUnknownEventNamesTheAcceptedValues() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("file-watch:" + dir + "?events=CREATE,MOVE").to("mock:events");
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            Throwable cause = e;
            while (!(cause instanceof IllegalArgumentException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause.getMessage().contains("'MOVE'") && cause.getMessage().contains("[CREATE, DELETE, MODIFY]"),
                    cause.getMessage());
        }
    }

    @Test
    void aNonRecursiveWatchHashesOnlyTheFilesOfTheBaseDirectory() throws Exception {
        Path top = Files.writeString(dir.resolve("top.txt"), "camel");
        Path inner = Files.writeString(Files.createDirectories(dir.resolve("sub")).resolve("inner.txt"), "camel");
        Set<Path> hashed = ConcurrentHashMap.newKeySet();
        FileHasher hasher = path -> {
            hashed.add(path.toRealPath());
            return FileHash.fromLong(1L);
        };

        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("recordingHasher", hasher);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("file-watch:" + dir + "?recursive=false&fileHasher=#recordingHasher").to("mock:events");
                }
            });
            // the consumer registers the directories and hashes their files when it starts
            context.start();

            assertTrue(hashed.contains(top.toRealPath()), "hashed: " + hashed);
            assertFalse(hashed.contains(inner.toRealPath()), "a file of a subdirectory was hashed: " + hashed);
        }
    }

    @Test
    void anEndpointMadeWithoutTheComponentHasTheDocumentedDefaults() {
        FileWatchEndpoint endpoint = new FileWatchEndpoint();

        assertEquals(1, endpoint.getConcurrentConsumers());
        assertEquals(1, endpoint.getPollThreads());
        assertEquals(Integer.MAX_VALUE, endpoint.getQueueSize());
        assertTrue(endpoint.isUseFileHashing());
    }
}
