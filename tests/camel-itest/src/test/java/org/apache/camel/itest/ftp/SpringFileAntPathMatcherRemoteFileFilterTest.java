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
package org.apache.camel.itest.ftp;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.FtpServiceExtension;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.ftpserver.FtpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Unit testing FTP ant path matcher
 */
@CamelSpringTest
@ContextConfiguration
@DisabledOnOs({ OS.AIX, OS.WINDOWS, OS.SOLARIS })
public class SpringFileAntPathMatcherRemoteFileFilterTest {
    @RegisterExtension
    public static FtpServiceExtension ftpServiceExtension
            = new FtpServiceExtension("SpringFileAntPathMatcherRemoteFileFilterTest.ftpPort");

    protected FtpServer ftpServer;

    protected String expectedBody = "Godday World";
    @Autowired
    protected ProducerTemplate template;
    @EndpointInject("ref:myFTPEndpoint")
    protected Endpoint inputFTP;
    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Test
    void testAntPatchMatherFilter() throws Exception {

        result.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(inputFTP, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(inputFTP, "Bye World", Exchange.FILE_NAME, "bye.xml");
        template.sendBodyAndHeader(inputFTP, "Bad world", Exchange.FILE_NAME, "subfolder/badday.txt");
        template.sendBodyAndHeader(inputFTP, "Day world", Exchange.FILE_NAME, "day.xml");
        template.sendBodyAndHeader(inputFTP, expectedBody, Exchange.FILE_NAME, "subfolder/foo/godday.txt");

        result.assertIsSatisfied();
    }
}
