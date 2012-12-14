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
package org.apache.camel.component.file.strategy;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Tests the MarkerFileExclusiveReadLockStrategy in a multi-threaded scenario.
 */
public class MarkerFileExclusiveReadLockStrategyReadLockFailedTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/readlock/");
        createDirectory("target/readlock/in");
        super.setUp();
    }

    public void testReadLockFailed() throws Exception {
        // should only pickup the 2nd file, as we have a marker file for the first file
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        writeFiles();

        // wait for files to be fully done using oneExchangeDone
        assertMockEndpointsSatisfied();
        assertTrue(oneExchangeDone.matchesMockWaitTime());

        // we should generate an output for the 2nd file
        assertFileExists("target/readlock/out/file2.dat");

        // and the marker file from the 1st file is still there, and the 1st file as well
        assertFileExists("target/readlock/in/file1.dat.camelLock");
        assertFileExists("target/readlock/in/file1.dat");
    }

    private void writeFiles() throws Exception {
        log.debug("Writing files...");

        // create a camelLock file first, so file1 will not be picked up
        File lock = new File("target/readlock/in/file1.dat.camelLock");
        lock.createNewFile();

        template.sendBodyAndHeader("file:target/readlock/in", "Hello World", Exchange.FILE_NAME, "file1.dat");
        template.sendBodyAndHeader("file:target/readlock/in", "Bye World", Exchange.FILE_NAME, "file2.dat");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/readlock/in?readLock=markerFile")
                        .to("file:target/readlock/out")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
