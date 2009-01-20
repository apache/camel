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
import java.nio.channels.FileLock;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Unit test to verify exclusive read - that we do not poll files that is in progress of being written.
 */
public class FromFtpExclusiveReadRenameStrategyTest extends FtpServerTestSupport {
    private static final Log LOG = LogFactory.getLog(FromFtpExclusiveReadRenameStrategyTest.class);
    private static final boolean ON_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/slowfile?password=admin&readLock=rename&consumer.delay=500";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testPollFileWhileSlowFileIsBeingWritten() throws Exception {
        // can only be tested on Windows
        if (!ON_WINDOWS) {
            return;
        }

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").process(new MySlowFileProcessor());
                from(getFtpUrl()).to("mock:result");
            }
        });
        context.start();

        deleteDirectory("./res/home");
        createDirectory("./res/home/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello WorldLine #0Line #1Line #2Bye World");
        mock.setMinimumResultWaitTime(3000);

        // send a message to seda:start to trigger the creating of the slowfile to poll
        template.sendBody("seda:start", "Create the slow file");

        mock.assertIsSatisfied();
    }

    public void testPollFileWhileSlowFileIsBeingWrittenWithTimeout() throws Exception {
        // can only be tested on Windows
        if (!ON_WINDOWS) {
            return;
        }

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").process(new MySlowFileProcessor());
                from(getFtpUrl() + "&readLockTimeout=1000").to("mock:result");
            }
        });
        context.start();

        deleteDirectory("./res/home");
        createDirectory("./res/home/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setMinimumResultWaitTime(2000);
        mock.setResultWaitTime(5000);

        // send a message to seda:start to trigger the creating of the slowfile to poll
        template.sendBody("seda:start", "Create the slow file");

        mock.assertIsSatisfied();
    }


    private class MySlowFileProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            LOG.info("Creating a slow file ...");
            File file = new File("./res/home/slowfile/hello.txt");
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
    }

}
