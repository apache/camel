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
import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpStreamingMoveTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/mymove?password=admin&delay=1000&streamDownload=true&move=done&stepwise=false";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/mymove");
    }

    @Test
    public void testStreamDownloadMove() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        sendFile(getFtpUrl(), "Hello World", "hello.txt");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        GenericFile<?> remoteFile = (GenericFile<?>)mock.getExchanges().get(0).getIn().getBody();
        assertTrue(remoteFile.getBody() instanceof InputStream);

        // give time for consumer to rename file
        Thread.sleep(1000);

        File file = new File(FTP_ROOT_DIR + "/mymove/done/hello.txt");
        assertTrue(file.exists(), "File should have been renamed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).routeId("foo").noAutoStartup().to("mock:result");
            }
        };
    }
}
