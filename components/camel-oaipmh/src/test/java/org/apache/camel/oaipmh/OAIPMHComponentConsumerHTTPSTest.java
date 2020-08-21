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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.oaipmh.utils.JettyTestServer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OAIPMHComponentConsumerHTTPSTest extends CamelTestSupport {

    @Test
    public void testOAIPMH() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied(5 * 1000);
    }

    @BeforeAll
    public static void startServer() throws IOException {
        //Mocked data  taken from https://repositorio.cepal.org/oai/request - July 21, 2020
        JettyTestServer.getInstance().context = "test4";
        JettyTestServer.getInstance().https = true;
        JettyTestServer.getInstance().startServer();
    }

    @AfterAll
    public static void stopServer() {
        JettyTestServer.getInstance().stopServer();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("oaipmh://localhost:" + JettyTestServer.getInstance().portssl + "/oai/request?"
                     + "ssl=true&"
                     + "ignoreSSLWarnings=true&"
                     + "delay=1000&"
                     + "verb=Identify&"
                     + "initialDelay=1000")
                             .to("mock:result");

            }
        };
    }
}
