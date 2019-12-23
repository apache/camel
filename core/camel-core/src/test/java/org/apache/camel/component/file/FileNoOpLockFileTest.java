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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * Unit test to verify that the noop file strategy usage of lock files.
 */
public class FileNoOpLockFileTest extends ContextTestSupport {

    @Override
    @After
    public void tearDown() throws Exception {
        deleteDirectory("target/data/reports");
        super.tearDown();
    }

    @Test
    public void testLocked() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Locked");

        template.sendBodyAndHeader("file:target/data/reports/locked", "Hello Locked", Exchange.FILE_NAME, "report.txt");

        mock.assertIsSatisfied();

        // sleep to let file consumer do its unlocking
        await().atMost(1, TimeUnit.SECONDS).until(() -> existsLockFile(false));

        // should be deleted after processing
        checkLockFile(false);
    }

    @Test
    public void testNotLocked() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Not Locked");

        template.sendBodyAndHeader("file:target/data/reports/notlocked", "Hello Not Locked", Exchange.FILE_NAME, "report.txt");

        mock.assertIsSatisfied();

        // sleep to let file consumer do its unlocking
        await().atMost(1, TimeUnit.SECONDS).until(() -> existsLockFile(false));

        // no lock files should exists after processing
        checkLockFile(false);
    }

    private static boolean existsLockFile(boolean expected) {
        String filename = "target/data/reports/";
        filename += expected ? "locked/" : "notlocked/";
        filename += "report.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;

        File file = new File(filename);
        return expected == file.exists();
    }

    private static void checkLockFile(boolean expected) {
        String filename = "target/data/reports/";
        filename += expected ? "locked/" : "notlocked/";
        filename += "report.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;

        File file = new File(filename);
        assertEquals("Lock file should " + (expected ? "exists" : "not exists"), expected, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // for locks
                from("file://target/data/reports/locked/?initialDelay=0&delay=10&noop=true&readLock=markerFile").process(new MyNoopProcessor()).to("mock:report");

                // for no locks
                from("file://target/data/reports/notlocked/?initialDelay=0&delay=10&noop=true&readLock=none").process(new MyNoopProcessor()).to("mock:report");
            }
        };
    }

    private static class MyNoopProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            boolean locked = "Hello Locked".equals(body);
            checkLockFile(locked);
        }
    }

}
