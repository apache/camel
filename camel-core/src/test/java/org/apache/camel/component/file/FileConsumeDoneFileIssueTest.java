/**
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

/**
 * CAMEL-5848
 */
public class FileConsumeDoneFileIssueTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");

        super.setUp();
    }

    public void testFileConsumeDoneFileIssue() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        template.sendBodyAndHeader("file:target/done", "A", Exchange.FILE_NAME, "foo-a.txt");
        template.sendBodyAndHeader("file:target/done", "B", Exchange.FILE_NAME, "foo-b.txt");
        template.sendBodyAndHeader("file:target/done", "C", Exchange.FILE_NAME, "foo-c.txt");
        template.sendBodyAndHeader("file:target/done", "D", Exchange.FILE_NAME, "foo-d.txt");
        template.sendBodyAndHeader("file:target/done", "E", Exchange.FILE_NAME, "foo-e.txt");
        template.sendBodyAndHeader("file:target/done", "E", Exchange.FILE_NAME, "foo.done");

        assertTrue("Done file should exists", new File("target/done/foo.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E");

        context.startRoute("foo");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/done/foo.done").exists());
    }
    
    public void testFileConsumeDynamicDoneFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader("file:target/done2", "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/done2", "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/done2", "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file:target/done2", "a", Exchange.FILE_NAME, "a.txt.done");
        template.sendBodyAndHeader("file:target/done2", "b", Exchange.FILE_NAME, "b.txt.done");
        template.sendBodyAndHeader("file:target/done2", "c", Exchange.FILE_NAME, "c.txt.done");
        
        assertTrue("Done file should exists", new File("target/done2/a.txt.done").exists());
        assertTrue("Done file should exists", new File("target/done2/b.txt.done").exists());
        assertTrue("Done file should exists", new File("target/done2/c.txt.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/done2/a.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/done2/b.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/done2/c.txt.done").exists());
        
    }
    
    public void testFileDoneFileNameContainingDollarSign() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader("file:target/done2", "A", Exchange.FILE_NAME, "$a$.txt");
        template.sendBodyAndHeader("file:target/done2", "B", Exchange.FILE_NAME, "$b.txt");
        template.sendBodyAndHeader("file:target/done2", "C", Exchange.FILE_NAME, "c$.txt");
        template.sendBodyAndHeader("file:target/done2", "a", Exchange.FILE_NAME, "$a$.txt.done");
        template.sendBodyAndHeader("file:target/done2", "b", Exchange.FILE_NAME, "$b.txt.done");
        template.sendBodyAndHeader("file:target/done2", "c", Exchange.FILE_NAME, "c$.txt.done");
        
        assertTrue("Done file should exists", new File("target/done2/$a$.txt.done").exists());
        assertTrue("Done file should exists", new File("target/done2/$b.txt.done").exists());
        assertTrue("Done file should exists", new File("target/done2/c$.txt.done").exists());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.startRoute("bar");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        Thread.sleep(50);

        // the done file should be deleted
        assertFalse("Done file should be deleted", new File("target/done2/$a$.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/done2/$b.txt.done").exists());
        assertFalse("Done file should be deleted", new File("target/done2/c$.txt.done").exists());
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/done?doneFileName=foo.done&initialDelay=0&delay=10").routeId("foo").noAutoStartup()
                    .convertBodyTo(String.class)
                    .to("mock:result");
                
                from("file:target/done2?doneFileName=${file:name}.done&initialDelay=0&delay=10")
                    .routeId("bar").noAutoStartup()
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }
}
