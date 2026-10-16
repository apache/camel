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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test that ftp consumer will not match directories (CAMEL-920)
 */
public class FtpConsumerDirectoriesNotMatchedIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/dirnotmatched/?password=admin"
               + "&recursive=true&include=^.*txt$&noop=true";
    }

    private String getFtpProducerUrl(String subDir) {
        return "ftp://admin@localhost:{{ftp.server.port}}/dirnotmatched/" + subDir + "?password=admin";
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareFtpServer();
    }

    @Test
    public void testSkipDirectories() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME,
                "readme.txt", "2007/report2007.txt", "2008/report2008.txt");
        mock.assertIsSatisfied();
    }

    private void prepareFtpServer() {
        // prepares the FTP Server by creating files on the server that we want
        // to unit test that we can poll and skip non-matching files and directories

        sendFile(getFtpProducerUrl(""), "This is a dot file", ".skipme");
        sendFile(getFtpProducerUrl(""), "This is a web file", "index.html");
        sendFile(getFtpProducerUrl(""), "This is a readme file", "readme.txt");
        sendFile(getFtpProducerUrl("2007"), "2007 report", "report2007.txt");
        sendFile(getFtpProducerUrl("2008"), "2008 report", "report2008.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
