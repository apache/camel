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
import org.apache.camel.component.file.remote.sftp.integration.SftpServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
public class SftpPublicKeyAcceptedAlgorithmsIT extends SftpServerTestSupport {

    @Test
    public void testSingleKey() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/publicKeyAcceptedAlgorithms" +
                                   "?password=admin" +
                                   "&publicKeyAcceptedAlgorithms=rsa-sha2-512",
                "a", Exchange.FILE_NAME,
                "a.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testMultipleKey() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/publicKeyAcceptedAlgorithms" +
                                   "?password=admin" +
                                   "&publicKeyAcceptedAlgorithms=rsa-sha2-512,not-supported-key",
                "a", Exchange.FILE_NAME,
                "a.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl()).routeId("myRoute").to("mock:result");
            }
        };
    }

    protected String getFtpUrl() {
        return "sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/publicKeyAcceptedAlgorithms/?password=admin"
               + "&noop=true";
    }
}
