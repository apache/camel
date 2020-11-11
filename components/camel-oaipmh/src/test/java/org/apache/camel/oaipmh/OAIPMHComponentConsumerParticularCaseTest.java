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
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OAIPMHComponentConsumerParticularCaseTest extends CamelTestSupport {

    @Test
    public void testOAIPMH() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(45);
        mock.assertIsSatisfied(5 * 1000);
    }

    @BeforeAll
    public static void startServer() throws IOException {
        //Mocked data  taken from https://revista.uisrael.edu.ec/index.php?page=oai - July 21, 2020
        JettyTestServer.getInstance().context = "test3";
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
                from("oaipmh:localhost:" + JettyTestServer.getInstance().port + "/index.php?page=oai&"
                     + "delay=1000&"
                     + "from=2020-02-01T00:00:00Z&"
                     + "initialDelay=1000")
                             .split(xpath(
                                     "/default:OAI-PMH/default:ListRecords/default:record/default:metadata/oai_dc:dc/dc:title/text()",
                                     new Namespaces("default", "http://www.openarchives.org/OAI/2.0/")
                                             .add("oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/")
                                             .add("dc", "http://purl.org/dc/elements/1.1/")))
                             //Log the titles of the records
                             .to("log:titles")
                             .to("mock:result");

            }
        };
    }
}
