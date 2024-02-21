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

    @Test
    public void testFileConsumeDoneFileIssue() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        template.sendBodyAndHeader(fileUri() + "/done", "A", Exchange.FILE_NAME, "foo-a.txt");
        template.sendBodyAndHeader(fileUri() + "/done", "B", Exchange.FILE_NAME, "foo-b.txt");
        template.sendBodyAndHeader(fileUri() + "/done", "C", Exchange.FILE_NAME, "foo-c.txt");
        template.sendBodyAndHeader(fileUri() + "/done", "D", Exchange.FILE_NAME, "foo-d.txt");
        template.sendBodyAndHeader(fileUri() + "/done", "E", Exchange.FILE_NAME, "foo-e.txt");
        template.sendBodyAndHeader(fileUri() + "/done", "E", Exchange.FILE_NAME, "foo.done");

        assertTrue(Files.exists(testFile("done/foo.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile("done/foo.done")), "Done file should be deleted"));
    }

    @Test
    public void testFileConsumeDynamicDoneFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader(fileUri() + "/done2", "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "a", Exchange.FILE_NAME, "a.txt.done");
        template.sendBodyAndHeader(fileUri() + "/done2", "b", Exchange.FILE_NAME, "b.txt.done");
        template.sendBodyAndHeader(fileUri() + "/done2", "c", Exchange.FILE_NAME, "c.txt.done");

        assertTrue(Files.exists(testFile("done2/a.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile("done2/b.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile("done2/c.txt.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile("done2/a.txt.done")), "Done file should be deleted"));

        // the done file should be deleted
        assertFalse(Files.exists(testFile("done2/b.txt.done")), "Done file should be deleted");
        assertFalse(Files.exists(testFile("done2/c.txt.done")), "Done file should be deleted");

    }

    @Test
    public void testFileDoneFileNameContainingDollarSign() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader(fileUri() + "/done2", "A", Exchange.FILE_NAME, "$a$.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "B", Exchange.FILE_NAME, "$b.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "C", Exchange.FILE_NAME, "c$.txt");
        template.sendBodyAndHeader(fileUri() + "/done2", "a", Exchange.FILE_NAME, "$a$.txt.done");
        template.sendBodyAndHeader(fileUri() + "/done2", "b", Exchange.FILE_NAME, "$b.txt.done");
        template.sendBodyAndHeader(fileUri() + "/done2", "c", Exchange.FILE_NAME, "c$.txt.done");

        assertTrue(Files.exists(testFile("done2/$a$.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile("done2/$b.txt.done")), "Done file should exists");
        assertTrue(Files.exists(testFile("done2/c$.txt.done")), "Done file should exists");

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());

        // the done file should be deleted
        Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile("done2/$a$.txt.done")), "Done file should be deleted"));

        assertFalse(Files.exists(testFile("done2/$b.txt.done")), "Done file should be deleted");
        assertFalse(Files.exists(testFile("done2/c$.txt.done")), "Done file should be deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("done?doneFileName=foo.done&initialDelay=0&delay=10")).routeId("foo").noAutoStartup()
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri("done2?doneFileName=${file:name}.done&initialDelay=0&delay=10")).routeId("bar")
                        .noAutoStartup().convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
