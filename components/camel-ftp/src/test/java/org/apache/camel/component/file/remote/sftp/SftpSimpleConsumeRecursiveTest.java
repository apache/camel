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
package org.apache.camel.component.file.remote.sftp;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SftpSimpleConsumeRecursiveTest extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleConsumeRecursive() throws Exception {
        if (!canTest()) {
            return;
        }

        // create files using regular file
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR, "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR + "/foo", "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR + "/bar", "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR + "/bar/cake", "D", Exchange.FILE_NAME, "d.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&delay=10000&disconnect=true&recursive=true").routeId("foo")
                    .noAutoStartup().to("log:result", "mock:result");
            }
        };
    }
}
