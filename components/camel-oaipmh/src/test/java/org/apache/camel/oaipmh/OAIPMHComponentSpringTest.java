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
package org.apache.camel.oaipmh;

import java.io.IOException;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.oaipmh.utils.JettyTestServer;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class OAIPMHComponentSpringTest extends CamelSpringTestSupport {

    @Test
    public void test() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        template.sendBodyAndHeader("direct:start", "", "port", JettyTestServer.getInstance().port);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied(3 * 1);
    }

    @BeforeAll
    public static void startServer() throws IOException {
        //Mocked data  taken from https://dspace.ucuenca.edu.ec/oai/request - July 21, 2020
        JettyTestServer.getInstance().context = "test2";
        JettyTestServer.getInstance().startServer();

    }

    @AfterAll
    public static void stopServer() {
        JettyTestServer.getInstance().stopServer();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {

        return new ClassPathXmlApplicationContext("spring/SpringOAIPMHTest.xml");
    }

}
