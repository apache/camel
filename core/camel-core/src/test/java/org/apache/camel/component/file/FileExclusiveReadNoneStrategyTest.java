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
import java.io.FileOutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test to verify exclusive read option using *none*
 */
public class FileExclusiveReadNoneStrategyTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileExclusiveReadNoneStrategyTest.class);
    private String fileUrl = "file://target/data/exclusiveread/slowfile?noop=true&initialDelay=0&delay=10&readLock=none";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/exclusiveread");
        createDirectory("target/data/exclusiveread/slowfile");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").process(new MySlowFileProcessor());
                from(fileUrl + "&readLockTimeout=500").to("mock:result");
            }
        };
    }

    @Test
    public void testPollFileWhileSlowFileIsBeingWritten() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // send a message to seda:start to trigger the creating of the slowfile
        // to poll
        template.sendBody("seda:start", "Create the slow file");

        mock.assertIsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        LOG.debug("Body is: " + body);
        assertFalse("Should not wait and read the entire file", body.endsWith("Bye World"));
    }

    private static class MySlowFileProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            LOG.info("Creating a slow file with no locks...");
            File file = new File("target/data/exclusiveread/slowfile/hello.txt");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("Hello World".getBytes());
            for (int i = 0; i < 3; i++) {
                Thread.sleep(100);
                fos.write(("Line #" + i).getBytes());
                LOG.info("Appending to slowfile");
            }
            fos.write("Bye World".getBytes());
            fos.close();
            LOG.info("... done creating slowfile");
        }
    }

}
