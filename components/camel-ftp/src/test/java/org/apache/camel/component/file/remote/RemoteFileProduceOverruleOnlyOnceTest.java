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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 *
 */
public class RemoteFileProduceOverruleOnlyOnceTest extends FtpServerTestSupport {

    @Test
    public void testFileToFtp() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.FILE_NAME, "/sub/hello.txt");
        headers.put(Exchange.OVERRULE_FILE_NAME, "/sub/ruled.txt");
        template.sendBodyAndHeaders("direct:input", "Hello World", headers);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "/sub/hello.txt");
        mock.expectedFileExists(FTP_ROOT_DIR + "/out/sub/ruled.txt", "Hello World");
        mock.expectedFileExists("target/out/sub/hello.txt", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/out");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input").to("ftp://admin:admin@localhost:" + getPort() + "/out/").to("file://target/out", "mock:result");
            }
        };
    }
}
