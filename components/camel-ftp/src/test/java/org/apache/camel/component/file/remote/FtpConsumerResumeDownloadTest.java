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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpConsumerResumeDownloadTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/myserver/?password=admin&localWorkDirectory=target/lwd&resumeDownload=true&binary=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/lwd");
        deleteDirectory("target/out");

        super.setUp();

        // create file on FTP server to download
        createDirectory(FTP_ROOT_DIR + "/myserver");
        File temp = new File(FTP_ROOT_DIR + "/myserver", "hello.txt");
        temp.createNewFile();
        FileOutputStream fos = new FileOutputStream(temp);
        fos.write("Hello\nWorld\nI was here".getBytes());
        fos.close();

        // create in-progress file with partial download
        createDirectory("target/lwd");
        temp = new File("target/lwd/hello.txt.inprogress");
        temp.createNewFile();
        fos = new FileOutputStream(temp);
        fos.write("Hello\n".getBytes());
        fos.close();
    }

    @Test
    public void testResumeDownload() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello\nWorld\nI was here");

        // start route
        context.getRouteController().startRoute("myRoute");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        // and the out file should exists
        File out = new File("target/out/hello.txt");
        assertTrue(out.exists(), "file should exists");
        assertEquals("Hello\nWorld\nI was here", IOConverter.toString(out, null));

        // now the lwd file should be deleted
        File local = new File("target/lwd/hello.txt");
        assertFalse(local.exists(), "Local work file should have been deleted");

        // and so the in progress
        File temp = new File("target/lwd/hello.txt.inprogress");
        assertFalse(temp.exists(), "Local work file should have been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).routeId("myRoute").noAutoStartup().to("mock:result", "file://target/out");
            }
        };
    }

}
