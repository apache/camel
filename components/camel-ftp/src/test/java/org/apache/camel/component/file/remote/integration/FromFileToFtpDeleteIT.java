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
package org.apache.camel.component.file.remote.integration;

import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromFileToFtpDeleteIT extends FtpServerTestSupport {
    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}?password=admin";
    }

    @Test
    public void testFromFileToFtpDelete() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory, "delete"), "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(notify.matchesWaitTime());

        // file should be deleted
        assertFileNotExists(testDirectory.resolve("delete/hello.txt"));

        // file should exists on ftp server
        assertFileExists(service.ftpFile("hello.txt"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(TestSupport.fileUri(testDirectory, "delete?delete=true")).to(getFtpUrl()).to("mock:result");
            }
        };
    }
}
