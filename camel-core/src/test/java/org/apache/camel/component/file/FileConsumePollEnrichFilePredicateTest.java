/**
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
package org.apache.camel.component.file;

import java.util.function.Predicate;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class FileConsumePollEnrichFilePredicateTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/enrich");
        deleteDirectory("target/enrichdata");
        super.setUp();
    }

    public void testPollEnrichFileReject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived((Object) null);

        template.sendBody("seda:start", "Start");
        log.info("Sleeping for 1/4 sec before writing enrichdata file");
        Thread.sleep(250);
        template.sendBodyAndHeader("file://target/enrichdata", "O", Exchange.FILE_NAME, "AAA.dat");
        log.info("... write done");

        assertMockEndpointsSatisfied();
    }

    public void testPollEnrichFileAccept() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hi Camel!");

        template.sendBody("seda:start", "Start");
        log.info("Sleeping for 1/4 sec before writing enrichdata file");
        Thread.sleep(250);
        template.sendBodyAndHeader("file://target/enrichdata", "Hi Camel!", Exchange.FILE_NAME, "AAA.dat");
        log.info("... write done");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                    .setHeader(Exchange.FILE_PREDICATE, () -> (Predicate<GenericFile>) genericFile -> genericFile.getFileLength() > 4)
                    .pollEnrich("file://target/enrichdata?initialDelay=0&delay=10&move=.done", 1000)
                    .to("mock:result");
            }
        };
    }

}