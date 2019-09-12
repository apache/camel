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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

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
    public void testRemoveFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watchDelete");

        Files.delete(testFiles.get(0));
        Files.delete(testFiles.get(1));

        mock.expectedMessageCount(2);
        mock.setResultWaitTime(1000);
        mock.assertIsSatisfied();
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

        all.expectedMessageCount(8); // 2 directories, 6 files
        all.assertIsSatisfied();

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

        Files.write(testFiles.get(0), "Hello".getBytes(), StandardOpenOption.SYNC);

        mock.setExpectedCount(1);
        mock.setResultWaitTime(1000);
        mock.assertIsSatisfied();
        assertEquals("Hello", mock.getExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testCreateBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watchAll");

        for (int i = 0; i < 10; i++) {
            createFile(testPath(), i + "");
        }

        mock.expectedMessageCount(10);
        mock.expectedMessagesMatches(exchange -> exchange.getIn()
            .getHeader(FileWatchComponent.EVENT_TYPE_HEADER, FileEventEnum.class) == FileEventEnum.CREATE);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("file-watch://" + testPath())
                    .routeId("watchAll")
                    .to("mock:watchAll");

                from("file-watch://" + testPath() + "?events=CREATE&antInclude=*.txt")
                    .routeId("onlyTxtInRoot")
                    .to("mock:onlyTxtInRoot");

                from("file-watch://" + testPath() + "?events=CREATE&antInclude=*/*.txt")
                    .routeId("onlyTxtInSubdirectory")
                    .to("mock:onlyTxtInSubdirectory");

                from("file-watch://" + testPath() + "?events=CREATE&antInclude=**/*.txt")
                    .routeId("onlyTxtAnywhere")
                    .to("mock:onlyTxtAnywhere");

                from("file-watch://" + testPath() + "?events=CREATE")
                    .to("mock:watchCreate");

                from("file-watch://" + testPath() + "?events=MODIFY")
                    .to("mock:watchModify");

                from("file-watch://" + testPath() + "?events=DELETE")
                    .to("mock:watchDelete");

                from("file-watch://" + testPath() + "?events=DELETE,CREATE")
                    .to("mock:watchDeleteOrCreate");

                from("file-watch://" + testPath() + "?events=DELETE,MODIFY")
                    .to("mock:watchDeleteOrModify");
            }
        };
    }
}
