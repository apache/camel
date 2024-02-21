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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.HelloService;
import org.apache.camel.component.cxf.HelloServiceImpl;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileToCxfMessageDataFormatTest extends CamelSpringTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(FileToCxfMessageDataFormatTest.class);
    private static int port1 = CXFTestSupport.getPort1();

    private Server server;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/filetocxf");

        // set CXF
        ServerFactoryBean factory = new ServerFactoryBean();

        factory.setAddress("http://localhost:" + port1 + "/FileToCxfMessageDataFormatTest/router");
        factory.setServiceClass(HelloService.class);
        factory.setServiceBean(new HelloServiceImpl());

        server = factory.create();
        server.start();

        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        server.stop();
        server.destroy();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/spring/FileToCxfMessageDataFormatTest.xml");
    }

    @Test
    public void testFileToCxfMessageDataFormat() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/filetocxf", createBody(), Exchange.FILE_NAME, "payload.xml");

        MockEndpoint.assertIsSatisfied(context);

        String out = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(out);
        LOG.info("Reply payload as a String:\n{}", out);
        assertTrue(out.contains("echo Camel"), "Should invoke the echo operation");
    }

    private String createBody() throws Exception {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cxf=\"http://cxf.component.camel.apache.org/\">\n"
               + "   <soapenv:Header/>\n"
               + "   <soapenv:Body>\n"
               + "      <cxf:echo>\n"
               + "          <cxf:arg0>Camel</cxf:arg0>\n"
               + "      </cxf:echo>\n"
               + "   </soapenv:Body>\n"
               + "</soapenv:Envelope>";
    }
}
