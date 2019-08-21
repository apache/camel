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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * CAMEL-5848
 */
public class FileConsumeDoneFileIssueTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/done");

        super.setUp();
    }

    @Test
    public void testFileConsumeDoneFileIssue() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        template.sendBodyAndHeader("file:target/data/done", "A", Exchange.FILE_NAME, "foo-a.txt");
        template.sendBodyAndHeader("file:target/data/done", "B", Exchange.FILE_NAME, "foo-b.txt");
        template.sendBodyAndHeader("file:target/data/done", "C", Exchange.FILE_NAME, "foo-c.txt");
        template.sendBodyAndHeader("file:target/data/done", "D", Exchange.FILE_NAME, "foo-d.txt");
        template.sendBodyAndHeader("file:target/data/done", "E", Exchange.FILE_NAME, "foo-e.txt");
        template.sendBodyAndHeader("file:target/data/done", "E", Exchange.FILE_NAME, "foo.done");

        assertTrue("Done file should exists", new File("target/data/done/foo.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/data/done/foo.done").exists());
    }

    @Test
    public void testFileConsumeDynamicDoneFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader("file:target/data/done2", "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/done2", "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/data/done2", "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file:target/data/done2", "a", Exchange.FILE_NAME, "a.txt.done");
        template.sendBodyAndHeader("file:target/data/done2", "b", Exchange.FILE_NAME, "b.txt.done");
        template.sendBodyAndHeader("file:target/data/done2", "c", Exchange.FILE_NAME, "c.txt.done");

        assertTrue("Done file should exists", new File("target/data/done2/a.txt.done").exists());
        assertTrue("Done file should exists", new File("target/data/done2/b.txt.done").exists());
        assertTrue("Done file should exists", new File("target/data/done2/c.txt.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/data/done2/a.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/data/done2/b.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/data/done2/c.txt.done").exists());

    }

    @Test
    public void testFileDoneFileNameContainingDollarSign() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader("file:target/data/done2", "A", Exchange.FILE_NAME, "$a$.txt");
        template.sendBodyAndHeader("file:target/data/done2", "B", Exchange.FILE_NAME, "$b.txt");
        template.sendBodyAndHeader("file:target/data/done2", "C", Exchange.FILE_NAME, "c$.txt");
        template.sendBodyAndHeader("file:target/data/done2", "a", Exchange.FILE_NAME, "$a$.txt.done");
        template.sendBodyAndHeader("file:target/data/done2", "b", Exchange.FILE_NAME, "$b.txt.done");
        template.sendBodyAndHeader("file:target/data/done2", "c", Exchange.FILE_NAME, "c$.txt.done");

        assertTrue("Done file should exists", new File("target/data/done2/$a$.txt.done").exists());
        assertTrue("Done file should exists", new File("target/data/done2/$b.txt.done").exists());
        assertTrue("Done file should exists", new File("target/data/done2/c$.txt.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/data/done2/$a$.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/data/done2/$b.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/data/done2/c$.txt.done").exists());

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/done?doneFileName=foo.done&initialDelay=0&delay=10").routeId("foo").noAutoStartup().convertBodyTo(String.class).to("mock:result");

                from("file:target/data/done2?doneFileName=${file:name}.done&initialDelay=0&delay=10").routeId("bar").noAutoStartup().convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
