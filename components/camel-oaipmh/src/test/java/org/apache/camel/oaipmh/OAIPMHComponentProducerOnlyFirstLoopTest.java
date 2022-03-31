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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.oaipmh.utils.MockOaipmhServer;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OAIPMHComponentProducerOnlyFirstLoopTest extends CamelTestSupport {

    private static MockOaipmhServer mockOaipmhServer;

    @BeforeAll
    public static void startServer() {
        mockOaipmhServer = MockOaipmhServer.create();
        mockOaipmhServer.start();
    }

    @AfterAll
    public static void stopServer() {
        mockOaipmhServer.stop();
    }

    @Test
    public void testOAIPMH() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        template.sendBody("direct:start", "foo");
        resultEndpoint.expectedMessageCount(532);
        resultEndpoint.assertIsSatisfied(3 * 1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .setHeader("CamelOaimphFrom", constant("2020-06-01T00:00:00Z"))
                        .setHeader("CamelOaimphOnlyFirst", constant("true"))
                        .loopDoWhile(simple("${in.header.CamelOaimphResumptionToken} || ${body} == 'foo'"))
                        .to("oaipmh://localhost:" + mockOaipmhServer.getHttpPort() + "/oai/request")
                        .split(body())
                        .split(xpath(
                                "/default:OAI-PMH/default:ListRecords/default:record/default:metadata/oai_dc:dc/dc:title/text()",
                                new Namespaces("default", "http://www.openarchives.org/OAI/2.0/")
                                        .add("oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/")
                                        .add("dc", "http://purl.org/dc/elements/1.1/")))
                        .to("mock:result")
                        .end();
            }
        };
    }
}
