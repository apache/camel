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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Unit test to verify exclusive read - that we do not poll files that is in progress of being written.
 */
public class FromFtpExclusiveReadTest extends FtpServerTestSupport {

    private static final Log LOG = LogFactory.getLog(FromFtpExclusiveReadTest.class);

    private int port = 20090;
    private String ftpUrl = "ftp://admin@localhost:" + port + "/slowfile?password=admin&consumer.exclusiveReadLock=true&consumer.delay=500";

    public int getPort() {
        return port;
    }

    // TODO: Not possible to test in single JVM
    public void testPollFileWhileSlowFileIsBeingWritten() throws Exception {
        /*deleteDirectory("./res/home");
        createDirectory("./res/home/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello WorldLine #0Line #1Line #2Bye World");

        createSlowFile();

        mock.assertIsSatisfied();*/
    }

    private void createSlowFile() throws Exception {
        LOG.info("Creating a slow file ...");
        File file = new File("./res/home/slowfile/hello.txt");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write("Hello World".getBytes());
        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            fos.write(("Line #" + i).getBytes());
            LOG.info("Appending to slowfile");
        }
        fos.write("Bye World".getBytes());
        fos.close();
        LOG.info("... done creating slowfile");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

}
