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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Unit testing FTP configured using spring bean
 */
@CamelSpringTest
@ContextConfiguration
public class SpringFtpEndpointTest {
    @RegisterExtension
    public static FtpServiceExtension ftpServiceExtension = new FtpServiceExtension("SpringFtpEndpointTest.ftpPort");

    protected FtpServer ftpServer;

    @Autowired
    protected ProducerTemplate template;

    @EndpointInject("ref:myFTPEndpoint")
    protected Endpoint inputFTP;

    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Test
    void testFtpEndpointAsSpringBean() throws Exception {
        result.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(inputFTP, "Hello World", Exchange.FILE_NAME, "hello.txt");

        result.assertIsSatisfied();
    }
}
