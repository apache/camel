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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test to verify *NON* exclusive read.
 */
public class FromFtpExclusiveReadNoneStrategyTest extends FtpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FromFtpExclusiveReadNoneStrategyTest.class);

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/slowfile?password=admin"
                + "&readLock=none&consumer.delay=500";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testPollFileWhileSlowFileIsBeingWrittenUsingNonExclusiveRead() throws Exception {
        // cannot test on windows due file system works differently with file locks
        if (isPlatform("windows")) {
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

        deleteDirectory(FTP_ROOT_DIR);
        createDirectory(FTP_ROOT_DIR + "/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // send a message to seda:start to trigger the creating of the slowfile to poll
        template.sendBody("seda:start", "Create the slow file");

        mock.assertIsSatisfied();

        // we read only part of the file as we dont have exclusive read and thus read part of the
        // file currently in progress of being written - so we get only the Hello World part
        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        LOG.debug("Body is: " + body);
        assertFalse("Should not wait and read the entire file", body.endsWith("Bye World"));
    }

    private static class MySlowFileProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            LOG.info("Creating a slow file ...");
            File file = new File(FTP_ROOT_DIR + "/slowfile/hello.txt");
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
