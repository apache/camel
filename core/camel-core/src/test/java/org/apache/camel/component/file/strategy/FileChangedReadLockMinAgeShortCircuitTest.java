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
package org.apache.camel.component.file.strategy;

import java.io.FileOutputStream;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileChangedReadLockMinAgeShortCircuitTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileChangedReadLockMinAgeShortCircuitTest.class);

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/changed/");
        createDirectory("target/data/changed/in");
        writeFile();
        // sleep to make the file a little bit old
        Thread.sleep(100);
        super.setUp();
    }

    @Test
    public void testChangedReadLockMinAge() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/data/changed/out/file.dat");
        // We should get the file on the first poll
        mock.expectedMessagesMatches(exchangeProperty(Exchange.RECEIVED_TIMESTAMP).convertTo(long.class).isLessThan(new Date().getTime() + 15000));

        assertMockEndpointsSatisfied();
    }

    private void writeFile() throws Exception {
        LOG.debug("Writing file...");

        FileOutputStream fos = new FileOutputStream("target/data/changed/in/file.dat");
        fos.write("Line".getBytes());
        fos.flush();
        fos.close();
        LOG.debug("Writing file DONE...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/changed/in?initialDelay=0&delay=10&readLock=changed&readLockMinAge=10&readLockCheckInterval=30000&readLockTimeout=90000")
                    .to("file:target/data/changed/out", "mock:result");
            }
        };
    }
}
