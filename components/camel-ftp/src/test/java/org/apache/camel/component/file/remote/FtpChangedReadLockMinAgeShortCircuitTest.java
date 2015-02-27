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
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FtpChangedReadLockMinAgeShortCircuitTest extends FtpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FtpChangedReadLockMinAgeShortCircuitTest.class);

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/changed?password=admin&readLock=changed&readLockMinAge=500&readLockCheckInterval=30000&readLockTimeout=90000&delete=true";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        writeFile();
        Thread.sleep(1000);
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/changed/out/slowfile.dat");
        // We should get the file on the first poll
        mock.expectedMessagesMatches(property(Exchange.RECEIVED_TIMESTAMP).isLessThan(new Date().getTime() + 15000));

        assertMockEndpointsSatisfied();
    }

    private void writeFile() throws Exception {
        LOG.debug("Writing file...");

        createDirectory(FTP_ROOT_DIR + "/changed");
        FileOutputStream fos = new FileOutputStream(FTP_ROOT_DIR + "/changed/slowfile.dat", true);
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
                from(getFtpUrl()).to("file:target/changed/out", "mock:result");
            }
        };
    }

}
