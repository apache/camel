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
import java.io.FileOutputStream;
import java.nio.channels.FileLock;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Unit test to verify exclusive read - that we do not poll files that is in progress of being written.
 */
public class FileExclusiveReadTest extends ContextTestSupport {

    private static final Log LOG = LogFactory.getLog(FileExclusiveReadTest.class);

    private String fileUrl = "file://target/exclusiveread/slowfile?consumer.delay=500&consumer.exclusiveReadLock=true";

    @Override
    protected void setUp() throws Exception {
        disableJMX();
        super.setUp();
    }

    public void testPoolIn3SecondsButNoFiles() throws Exception {
        deleteDirectory("./target/exclusiveread");
        createDirectory("./target/exclusiveread/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Thread.sleep(3 * 1000L);

        mock.assertIsSatisfied();
    }

    // TODO: Not possible to test in the same JVM (see javadoc for FileLock)
    public void xxxtestPollFileWhileSlowFileIsBeingWritten() throws Exception {
        deleteDirectory("./target/exclusiveread");
        createDirectory("./target/exclusiveread/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello WorldLine #0Line #1Line #2Bye Worl");

        createSlowFile();

        mock.assertIsSatisfied();
    }

    private void createSlowFile() throws Exception {
        LOG.info("Creating a slow file ...");
        File file = new File("./target/exclusiveread/slowfile/hello.txt");
        FileOutputStream fos = new FileOutputStream(file);
        FileLock lock = fos.getChannel().lock();
        fos.write("Hello World".getBytes());
        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            fos.write(("Line #" + i).getBytes());
            LOG.info("Appending to slowfile");
        }
        fos.write("Bye World".getBytes());
        lock.release();
        fos.close();
        LOG.info("... done creating slowfile");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).to("mock:result");
            }
        };
    }

}
