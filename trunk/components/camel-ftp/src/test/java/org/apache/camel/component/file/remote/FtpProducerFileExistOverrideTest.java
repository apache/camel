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
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FtpProducerFileExistOverrideTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/exist?password=admin&delay=2000&noop=true&fileExist=Override";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");
    }

    @Test
    public void testOverride() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedFileExists(FTP_ROOT_DIR + "/exist/hello.txt", "Bye World");

        template.sendBodyAndHeader(getFtpUrl(), "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}