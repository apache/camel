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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class FtpProducerAllowNullBodyFileAlreadyExistTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/allow?password=admin";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        template.sendBodyAndHeader(getFtpUrl(), "Hello world", Exchange.FILE_NAME, "hello.txt");
    }

    @Test
    public void testFileExistAppendAllowNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:appendTypeAppendResult");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(FTP_ROOT_DIR + "/allow/hello.txt", "Hello world");

        template.sendBody("direct:appendTypeAppend", null);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFileExistOverrideAllowNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:appendTypeOverrideResult");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(FTP_ROOT_DIR + "/allow/hello.txt", "");

        template.sendBody("direct:appendTypeOverride", null);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:appendTypeAppend")
                        .setHeader(Exchange.FILE_NAME, constant("hello.txt"))
                        .to(getFtpUrl() + "&allowNullBody=true&fileExist=Append")
                        .to("mock:appendTypeAppendResult");

                from("direct:appendTypeOverride")
                        .setHeader(Exchange.FILE_NAME, constant("hello.txt"))
                        .to(getFtpUrl() + "&allowNullBody=true&fileExist=Override")
                        .to("mock:appendTypeOverrideResult");
            }
        };
    }

}