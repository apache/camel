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

import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.Before;
import org.junit.Test;

/**
 * User does not have write permissions so can't deleted consumed file.
 */
public class FtpConsumerDeleteNoWritePermissionTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://dummy@localhost:" + getPort() + "/deletenoperm?password=foo"
                + "&delete=true&consumer.delay=5000";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testConsumerDeleteNoWritePermission() throws Exception {
        PollingConsumer consumer = context.getEndpoint(getFtpUrl()).createPollingConsumer();
        consumer.start();
        Exchange out = consumer.receive(3000);
        assertNotNull("Should get the file", out);

        try {
            // give consumer time to try to delete the file
            Thread.sleep(1000);
            consumer.stop();
        } catch (GenericFileOperationFailedException fofe) {
            // expected, ignore
        }
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool and store as a local file
        String ftpUrl = "ftp://admin@localhost:" + getPort() + "/deletenoperm/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello World", Exchange.FILE_NAME, "hello.txt");
    }
}