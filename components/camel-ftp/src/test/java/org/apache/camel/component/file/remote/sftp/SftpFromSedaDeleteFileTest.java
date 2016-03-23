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
package org.apache.camel.component.file.remote.sftp;

import java.io.File;
import java.io.IOException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test to test delete option.
 */
public class SftpFromSedaDeleteFileTest extends SftpServerTestSupport {

    protected String getFtpUrl() {
        return "sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR
                + "?username=admin&knownHostsFile=./src/test/resources/known_hosts&privateKeyFile=./src/test/resources/id_rsa"
                + "&privateKeyPassphrase=secret&delay=500&disconnect=false&delete=true";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        createSampleFile();
    }
    
    @Test
    public void testPollFileAndShouldBeDeleted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World this file will be deleted");

        mock.assertIsSatisfied();

        Thread.sleep(500);

        // assert the file is deleted
        File file = new File(FTP_ROOT_DIR + "/hello.txt");
        assertFalse("The file should have been deleted", file.exists());
    }

    private void createSampleFile() throws IOException {
        File file = new File(FTP_ROOT_DIR + "/" + "foo.txt");

        FileUtils.write(file, "Hello World this file will be deleted");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("seda:foo");

                from("seda:foo")
                        .delay(750)
                        .log("${body}")
                        .delay(750)
                        .to("mock:result");
            }
        };
    }
}