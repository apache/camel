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

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-5848
 */
public class FileConsumeDoneFileIssueTest extends ContextTestSupport {
    private static final String TEST_DIR_NAME = "done" + UUID.randomUUID().toString();
    private static final String TEST_DIR_NAME_2 = "done2" + UUID.randomUUID().toString();

    @Test
    public void testFileConsumeDoneFileIssue() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "A", Exchange.FILE_NAME, "foo-a.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "B", Exchange.FILE_NAME, "foo-b.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "C", Exchange.FILE_NAME, "foo-c.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "D", Exchange.FILE_NAME, "foo-d.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "E", Exchange.FILE_NAME, "foo-e.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME, "E", Exchange.FILE_NAME, "foo.done");

        assertTrue(Files.exists(testFile(TEST_DIR_NAME + File.separator + "foo.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile(TEST_DIR_NAME + File.separator + "foo.done")),
                        "Done file should be deleted"));
    }

    @Test
    public void testFileConsumeDynamicDoneFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "a", Exchange.FILE_NAME, "a.txt.done");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "b", Exchange.FILE_NAME, "b.txt.done");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "c", Exchange.FILE_NAME, "c.txt.done");

        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "a.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "b.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "c.txt.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "a.txt.done")),
                        "Done file should be deleted"));

        // the done file should be deleted
        assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "b.txt.done")), "Done file should be deleted");
        assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "c.txt.done")), "Done file should be deleted");

    }

    @Test
    public void testFileDoneFileNameContainingDollarSign() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "A", Exchange.FILE_NAME, "$a$.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "B", Exchange.FILE_NAME, "$b.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "C", Exchange.FILE_NAME, "c$.txt");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "a", Exchange.FILE_NAME, "$a$.txt.done");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "b", Exchange.FILE_NAME, "$b.txt.done");
        template.sendBodyAndHeader(fileUri() + File.separator + TEST_DIR_NAME_2, "c", Exchange.FILE_NAME, "c$.txt.done");

        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "$a$.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "$b.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "c$.txt.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "$a$.txt.done")),
                        "Done file should be deleted"));

        assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "$b.txt.done")), "Done file should be deleted");
        assertFalse(Files.exists(testFile(TEST_DIR_NAME_2 + File.separator + "c$.txt.done")), "Done file should be deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(TEST_DIR_NAME + "?doneFileName=foo.done&initialDelay=0&delay=10")).routeId("foo")
                        .autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri(TEST_DIR_NAME_2 + "?doneFileName=${file:name}.done&initialDelay=0&delay=10")).routeId("bar")
                        .autoStartup(false).convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
