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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class FileWatchComponentRecursiveTest extends FileWatchComponentTestBase {

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file-watch://" + testPath() + "?recursive=true").to("mock:recursive");
                from("file-watch://" + testPath() + "?recursive=false").to("mock:nonRecursive");
            }
        };
    }

    @Test
    public void testCreateFileInSubdirectories() throws Exception {
        Path newDir = Paths.get(testPath(), "a", "b", "c", "d");
        newDir = Files.createDirectories(newDir);
        File newFile = new File(newDir.toFile(), UUID.randomUUID().toString());

        if (!newFile.createNewFile()) {
            throw new RuntimeException("cannot create file");
        }
        MockEndpoint recursive = getMockEndpoint("mock:recursive");
        recursive.expectedMessageCount(5); // 4 directories and one file
        recursive.assertIsSatisfied();

        MockEndpoint nonRecursive = getMockEndpoint("mock:nonRecursive");
        nonRecursive.expectedMessageCount(1); // 1 directory
        nonRecursive.assertIsSatisfied();
    }
}
