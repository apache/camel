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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpConsumerResumeDownloadIT extends FtpServerTestSupport {

    @TempDir
    Path lwd;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/myserver/?password=admin&localWorkDirectory=" + lwd.resolve("lwd")
               + "&resumeDownload=true&binary=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // create file on FTP server to download
        Path myserver = service.ftpFile("myserver");
        createDirectory(myserver);
        Files.write(myserver.resolve("hello.txt"), "Hello\nWorld\nI was here".getBytes());

        // create in-progress file with partial download
        Files.write(lwd.resolve("hello.txt.inprogress"), "Hello\n".getBytes());
    }

    @Test
    public void testResumeDownload() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello\nWorld\nI was here");

        // start route
        context.getRouteController().startRoute("myRoute");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(notify.matchesWaitTime());

        // and the out file should exists
        assertFileExists(lwd.resolve("out/hello.txt"), "Hello\nWorld\nI was here");

        // now the lwd file should be deleted
        assertFileNotExists(lwd.resolve("lwd/hello.txt"));

        // and so the in progress
        assertFileNotExists(lwd.resolve("lwd/hello.txt.inprogress"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).routeId("myRoute").noAutoStartup().to("mock:result", TestSupport.fileUri(lwd, "out"));
            }
        };
    }

}
