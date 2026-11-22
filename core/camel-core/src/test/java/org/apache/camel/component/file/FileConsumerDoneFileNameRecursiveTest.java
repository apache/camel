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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With recursive=true and a done file with a fixed name, each directory has its own done file: a poll that consumes the
 * files of several directories deletes the done file of each of them, not only the one of the directory of its last
 * file.
 */
class FileConsumerDoneFileNameRecursiveTest extends ContextTestSupport {

    private static final List<String> DIRECTORIES = List.of("d1", "d2", "d3");

    @Test
    void pollOfSeveralDirectoriesDeletesTheDoneFileOfEach() throws Exception {
        for (String directory : DIRECTORIES) {
            Files.createDirectories(testDirectory(directory));
            Files.writeString(testFile(directory + "/" + directory + ".txt"), directory);
            Files.writeString(testFile(directory + "/ready"), "");
        }
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("d1", "d2", "d3");

        context.getRouteController().startRoute("files");

        assertMockEndpointsSatisfied();
        // the files are moved once their done file was handled
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> DIRECTORIES.stream()
                .noneMatch(directory -> Files.exists(testFile(directory + "/" + directory + ".txt"))));
        for (String directory : DIRECTORIES) {
            assertFalse(Files.exists(testFile(directory + "/ready")),
                    "The done file of " + directory + " must be deleted after its last file");
        }

        // a file written into one of the directories waits for a new done file
        Files.writeString(testFile("d1/next.txt"), "next");
        Files.writeString(testFile("d2/other.txt"), "other");
        Files.writeString(testFile("d2/ready"), "");
        getMockEndpoint("mock:result").reset();
        getMockEndpoint("mock:result").expectedBodiesReceived("other");
        assertMockEndpointsSatisfied();
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> !Files.exists(testFile("d2/ready")));
        assertTrue(Files.exists(testFile("d1/next.txt")), "d1/next.txt has no done file and must not be consumed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?doneFileName=ready&recursive=true&initialDelay=0&delay=10"))
                        .routeId("files").autoStartup(false)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
