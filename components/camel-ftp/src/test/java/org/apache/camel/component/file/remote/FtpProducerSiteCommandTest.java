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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;

public class FtpProducerSiteCommandTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/site?password=admin&siteCommand=help site";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:callSiteCommandWithoutUpload")
                        .to("ftp://admin@localhost:" + getPort() + "/site?password=admin&siteCommand=STAT&upload=false");

                from("direct:callSiteCommandAndCaptureOutput")
                        .inOut("ftp://admin@localhost:" + getPort() + "/site?password=admin&siteCommand=STAT&upload=false&siteCommandCapture=true");

                from("direct:callTwoSiteCommands")
                        .inOut("ftp://admin@localhost:" + getPort() + "/site?password=admin&siteCommand=STAT%0AZONE&upload=false&siteCommandCapture=true");
            }
        };
    }

    @Test
    public void testSiteCommandWithoutUploadingFile() throws Exception {
        template.sendBodyAndHeader("direct:callSiteCommandWithoutUpload", "Hello world", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/site/hello.txt");
        assertFalse("No file should be uploaded", file.exists());
    }

    @Test
    public void testCaptureSiteCommandOutput() throws Exception {
        List<String> output = template.requestBody("direct:callSiteCommandAndCaptureOutput", null, List.class);

        assertThat("Body should contain one line of site command output", output.size(), is(1));
        assertThat("Site command output should start with response code 200", output.get(0), startsWith("200"));
    }

    @Test
    public void testCallsTwoSiteCommands() throws Exception {
        List<String> output = template.requestBody("direct:callTwoSiteCommands", null, List.class);
        System.out.println(output.get(0));
        System.out.println(output.get(1));
        assertThat("Body should contain one line of site command output", output.size(), is(2));
    }

    @Test
    public void testSiteCommand() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/site/hello.txt");
        assertTrue("The uploaded file should exist", file.exists());
        assertEquals("Hello World", IOConverter.toString(file, null));
    }
}