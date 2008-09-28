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

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.file.FileComponent;

/**
 * Unit test for login failure due bad password and login with accepted password
 */
public class FtpLoginTest extends FtpServerTestSupport {

    private int port = 20077;
    private String ftpUrl = "ftp://dummy@localhost:" + port;

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("./res/home/login");
    }

    public void testBadLogin() throws Exception {
        try {
            uploadFile("dummy", "cantremeber");
            fail("Should have thrown a FtpOperationFailedException");
        } catch (FtpOperationFailedException e) {
            // expected
            assertEquals(530, e.getCode());
        }

        // assert file NOT created
        File file = new File("./res/home/login/report.txt");
        file = file.getAbsoluteFile();
        assertFalse("The file should NOT exists", file.exists());
    }

    public void testGoodLogin() throws Exception {
        uploadFile("scott", "tiger");

        // give time for producer
        Thread.sleep(2000);

        // assert file created
        File file = new File("./res/home/login/report.txt");
        file = file.getAbsoluteFile();
        assertTrue("The file should exists", file.exists());
    }

    private void uploadFile(String username, String password) throws Exception {
        RemoteFileConfiguration config = new RemoteFileConfiguration();
        config.setBinary(false);
        config.setUsername(username);
        config.setPassword(password);
        config.setDirectory(true);
        config.setHost("localhost");
        config.setPort(port);
        config.setProtocol("ftp");
        config.setFile("login");

        RemoteFileComponent component = new RemoteFileComponent(context);
        component.setConfiguration(config);

        RemoteFileEndpoint endpoint = new FtpEndpoint(ftpUrl, component, config);
        
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World from FTPServer");
        exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, "report.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

}
