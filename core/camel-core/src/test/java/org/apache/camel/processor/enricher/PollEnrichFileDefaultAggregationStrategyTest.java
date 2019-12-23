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
package org.apache.camel.processor.enricher;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class PollEnrichFileDefaultAggregationStrategyTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/enrich");
        deleteDirectory("target/data/enrichdata");
        super.setUp();
    }

    @Test
    public void testPollEnrichDefaultAggregationStrategyBody() throws Exception {

        getMockEndpoint("mock:start").expectedBodiesReceived("Start");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Big file");
        mock.expectedFileExists("target/data/enrich/.done/AAA.fin");
        mock.expectedFileExists("target/data/enrichdata/.done/AAA.dat");

        template.sendBodyAndHeader("file://target/data/enrich", "Start", Exchange.FILE_NAME, "AAA.fin");

        log.info("Sleeping for 0.25 sec before writing enrichdata file");
        Thread.sleep(250);
        template.sendBodyAndHeader("file://target/data/enrichdata", "Big file", Exchange.FILE_NAME, "AAA.dat");
        log.info("... write done");

        assertMockEndpointsSatisfied();

        assertFileDoesNotExists("target/data/enrichdata/AAA.dat.camelLock");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/enrich?initialDelay=0&delay=10&move=.done").to("mock:start")
                    .pollEnrich("file://target/data/enrichdata?initialDelay=0&delay=10&readLock=markerFile&move=.done", 10000).to("mock:result");
            }
        };
    }

    private static void assertFileDoesNotExists(String filename) {
        File file = new File(filename);
        assertFalse("File " + filename + " should not exist, it should have been deleted after being processed", file.exists());
    }
}
