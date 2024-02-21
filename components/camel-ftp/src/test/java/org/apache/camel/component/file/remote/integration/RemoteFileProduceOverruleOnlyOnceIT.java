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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 *
 */
public class RemoteFileProduceOverruleOnlyOnceIT extends FtpServerTestSupport {
    @TempDir
    Path testDirectory;

    @Test
    public void testFileToFtp() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.FILE_NAME, "/sub/hello.txt");
        headers.put(Exchange.OVERRULE_FILE_NAME, "/sub/ruled.txt");
        template.sendBodyAndHeaders("direct:input", "Hello World", headers);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "/sub/hello.txt");
        mock.expectedFileExists(service.ftpFile("out/sub/ruled.txt"), "Hello World");
        mock.expectedFileExists(testDirectory.resolve("out/sub/hello.txt"), "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input").to("ftp://admin:admin@localhost:{{ftp.server.port}}/out/").to(
                        TestSupport.fileUri(testDirectory, "out"),
                        "mock:result");
            }
        };
    }
}
