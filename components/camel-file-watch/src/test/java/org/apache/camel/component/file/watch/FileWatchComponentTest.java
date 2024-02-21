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
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileWatchComponentTest extends FileWatchComponentTestBase {

    @Test
    public void testCreateFile() throws Exception {
        MockEndpoint watchAll = getMockEndpoint("mock:watchAll");
        MockEndpoint watchCreate = getMockEndpoint("mock:watchCreate");
        MockEndpoint watchModify = getMockEndpoint("mock:watchModify");
        MockEndpoint watchDelete = getMockEndpoint("mock:watchDelete");
        MockEndpoint watchDeleteOrCreate = getMockEndpoint("mock:watchDeleteOrCreate");
        MockEndpoint watchDeleteOrModify = getMockEndpoint("mock:watchDeleteOrModify");

        File newFile = createFile(testPath(), UUID.randomUUID().toString());

        watchAll.expectedMessageCount(1);
        watchAll.setAssertPeriod(1000);
        watchAll.assertIsSatisfied();

        watchCreate.expectedMessageCount(1);
        watchCreate.setAssertPeriod(1000);
        watchCreate.assertIsSatisfied();

        watchDeleteOrCreate.expectedMessageCount(1);
        watchDeleteOrCreate.setAssertPeriod(1000);
        watchDeleteOrCreate.assertIsSatisfied();

        watchModify.expectedMessageCount(0);
        watchModify.setAssertPeriod(1000);
        watchModify.assertIsSatisfied();

        watchDelete.expectedMessageCount(0);
        watchDelete.setAssertPeriod(1000);
        watchDelete.assertIsSatisfied();

        watchDeleteOrModify.expectedMessageCount(0);
        watchDeleteOrModify.setAssertPeriod(1000);
        watchDeleteOrModify.assertIsSatisfied();

        assertFileEvent(newFile.getName(), FileEventEnum.CREATE, watchAll.getExchanges().get(0));
        assertFileEvent(newFile.getName(), FileEventEnum.CREATE, watchCreate.getExchanges().get(0));
        assertFileEvent(newFile.getName(), FileEventEnum.CREATE, watchDeleteOrCreate.getExchanges().get(0));
    }

    @Test
    public void testAntMatcher() throws Exception {
        MockEndpoint all = getMockEndpoint("mock:watchAll");
        MockEndpoint onlyTxtAnywhere = getMockEndpoint("mock:onlyTxtAnywhere");
        MockEndpoint onlyTxtInSubdirectory = getMockEndpoint("mock:onlyTxtInSubdirectory");
        MockEndpoint onlyTxtInRoot = getMockEndpoint("mock:onlyTxtInRoot");

        Path root = Paths.get(testPath());
        Path a = Paths.get(testPath(), "a");
        Path b = Paths.get(testPath(), "a", "b");

        Files.createDirectories(b);

        createFile(root.toFile(), "inRoot.txt");
        createFile(root.toFile(), "inRoot.java");
        createFile(a.toFile(), "inA.txt");
        createFile(a.toFile(), "inA.java");
        createFile(b.toFile(), "inB.txt");
        createFile(b.toFile(), "inB.java");

        /*
        On systems with slow IO, the time of creation and the time of notification may not be the reliably aligned (i.e; the
        notification may be sent while the creation is still in progress).
        As such, we have to be lenient checking for the expected number of exchanges received.
         */
        all.expectedMinimumMessageCount(8); // 2 directories, 6 files
        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> all.assertIsSatisfied());

        onlyTxtAnywhere.expectedMessageCount(3); // 3 txt files
        onlyTxtAnywhere.assertIsSatisfied();

        onlyTxtInSubdirectory.expectedMessageCount(1); // 1 txt file in first subdirectory
        onlyTxtInSubdirectory.assertIsSatisfied();

        onlyTxtInRoot.expectedMessageCount(1); // 1 txt file inRoot.txt (should exclude everything in subdirectories)
        onlyTxtInRoot.assertIsSatisfied();
    }

    @Test
    public void createModifyReadBodyAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watchAll");
        mock.setExpectedCount(1);
        mock.setResultWaitTime(1000);

        Files.write(testFiles.get(0), "Hello".getBytes(), StandardOpenOption.SYNC);

        mock.assertIsSatisfied();
        assertEquals("Hello", mock.getExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testCreateBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watchAll");
        mock.expectedMessageCount(10);
        mock.expectedMessagesMatches(exchange -> exchange.getIn()
                .getHeader(FileWatchConstants.EVENT_TYPE_HEADER, FileEventEnum.class) == FileEventEnum.CREATE);

        for (int i = 0; i < 10; i++) {
            createFile(testPath(), String.valueOf(i));
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF("file-watch://%s", testPath())
                        .routeId("watchAll")
                        .to("mock:watchAll");

                fromF("file-watch://%s?events=CREATE&antInclude=*.txt", testPath())
                        .routeId("onlyTxtInRoot")
                        .to("mock:onlyTxtInRoot");

                fromF("file-watch://%s?events=CREATE&antInclude=*/*.txt", testPath())
                        .routeId("onlyTxtInSubdirectory")
                        .to("mock:onlyTxtInSubdirectory");

                fromF("file-watch://%s?events=CREATE&antInclude=**/*.txt", testPath())
                        .routeId("onlyTxtAnywhere")
                        .to("mock:onlyTxtAnywhere");

                fromF("file-watch://%s?events=CREATE", testPath())
                        .to("mock:watchCreate");

                fromF("file-watch://%s?events=MODIFY", testPath())
                        .to("mock:watchModify");

                fromF("file-watch://%s?events=DELETE,CREATE", testPath())
                        .to("mock:watchDeleteOrCreate");

                fromF("file-watch://%s?events=DELETE,MODIFY", testPath())
                        .to("mock:watchDeleteOrModify");
            }
        };
    }
}
