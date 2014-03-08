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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.Test;

/**
 * Unit test for login failure due bad password and no re connect attempts allowed
 */
public class FtpThrowExceptionOnConnectionFailedTest extends FtpServerTestSupport {

    @Test
    public void testBadLogin() throws Exception {
        try {
            uploadFile("dummy", "cantremeber");
            fail("Should have thrown a GenericFileOperationFailedException");
        } catch (GenericFileOperationFailedException e) {
            // expected
            assertEquals(530, e.getCode());
        }

        // assert file NOT created
        File file = new File(FTP_ROOT_DIR + "login/report.txt");
        assertFalse("The file should NOT exists", file.exists());
    }

    private void uploadFile(String username, String password) throws Exception {
        Endpoint endpoint = context.getEndpoint("ftp://" + username + "@localhost:" + getPort() + "/login?password="
                + password + "&maximumReconnectAttempts=0&throwExceptionOnConnectFailed=true");

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World from FTPServer");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "report.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

}