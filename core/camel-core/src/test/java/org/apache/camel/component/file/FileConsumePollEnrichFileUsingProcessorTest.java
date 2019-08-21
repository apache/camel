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
package org.apache.camel.component.file;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.Before;
import org.junit.Test;

public class FileConsumePollEnrichFileUsingProcessorTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/enrich");
        deleteDirectory("target/data/enrichdata");
        super.setUp();
    }

    @Test
    public void testPollEnrich() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Start");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Big file");

        mock.expectedFileExists("target/data/enrich/.done/AAA.fin");
        mock.expectedFileExists("target/data/enrichdata/.done/AAA.dat");
        mock.expectedFileExists("target/data/enrichdata/BBB.dat");

        template.sendBodyAndHeader("file://target/data/enrichdata", "Big file", Exchange.FILE_NAME, "AAA.dat");
        template.sendBodyAndHeader("file://target/data/enrichdata", "Other Big file", Exchange.FILE_NAME, "BBB.dat");
        template.sendBodyAndHeader("file://target/data/enrich", "Start", Exchange.FILE_NAME, "AAA.fin");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/enrich?initialDelay=0&delay=10&move=.done").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String name = exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY, String.class);
                        name = FileUtil.stripExt(name) + ".dat";

                        // use a consumer template to get the data file
                        Exchange data = null;
                        ConsumerTemplate con = exchange.getContext().createConsumerTemplate();
                        try {
                            // try to get the data file
                            data = con.receive("file://target/data/enrichdata?initialDelay=0&delay=10&move=.done&fileName=" + name, 5000);
                        } finally {
                            // stop the consumer as it does not need to poll for
                            // files anymore
                            con.stop();
                        }

                        // if we found the data file then process it by sending
                        // it to the direct:data endpoint
                        if (data != null) {
                            template.send("direct:data", data);
                        } else {
                            // otherwise do a rollback
                            throw new CamelExchangeException("Cannot find the data file " + name, exchange);
                        }
                    }
                }).to("mock:start");

                from("direct:data").to("mock:result");
            }
        };
    }
}
