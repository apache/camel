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
import org.apache.camel.component.file.FileComponent;

/**
 * User does not have write permissions so can't deleted consumed file.
 */
public class FtpConsumerDeleteNoWritePermissionTest extends FtpServerTestSupport {

    private int port = 20087;

    private String ftpUrl = "ftp://dummy@localhost:" + port + "/deletenoperm?password=foo"
        + "&consumer.deleteFile=true";

    public void testExludePreAndPostfixes() throws Exception {
        PollingConsumer consumer = context.getEndpoint(ftpUrl).createPollingConsumer();
        consumer.start();
        Exchange out = consumer.receive(3000);
        assertNull("Should not get the file", out);

        try {
            consumer.stop();
        } catch (FtpOperationFailedException fofe) {
            // expected, ignore
        }
    }

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool and store as a local file
        String ftpUrl = "ftp://admin@localhost:" + port + "/deletenoperm/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");
    }

}