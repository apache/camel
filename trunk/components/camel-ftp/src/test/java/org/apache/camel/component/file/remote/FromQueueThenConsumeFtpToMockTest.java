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
package org.apache.camel.component.file.remote;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test based on user forum question
 */
public class FromQueueThenConsumeFtpToMockTest extends FtpServerTestSupport {

    // START SNIPPET: e1
    // we use delay=5000 to use 5 sec delay between pools to avoid polling a second time before we stop the consumer
    // this is because we only want to run a single poll and get the file
    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/getme?password=admin&binary=false&delay=5000";
    }
    // END SNIPPET: e1

    private String getStoreUrl() {
        return "ftp://admin@localhost:" + getPort() + "/getme?password=admin&binary=false";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testFromQueueThenConsumeFtp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("seda:start", "Hello World", "myfile", "hello.txt");

        assertMockEndpointsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want to unit
        // test that we can pool once
        Endpoint endpoint = context.getEndpoint(getStoreUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Bye World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e2
                from("seda:start").process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        // get the filename from our custome header we want to get from a remote server
                        String filename = exchange.getIn().getHeader("myfile", String.class);

                        // construct the total url for the ftp consumer
                        // add the fileName option with the file we want to consume
                        String url = getFtpUrl() + "&fileName=" + filename;

                        // create a ftp endpoint
                        Endpoint ftp = context.getEndpoint(url);

                        // create a polling consumer where we can poll the myfile from the ftp server
                        PollingConsumer consumer = ftp.createPollingConsumer();

                        // must start the consumer before we can receive
                        consumer.start();

                        // poll the file from the ftp server
                        Exchange result = consumer.receive();

                        // the result is the response from the FTP consumer (the downloaded file)
                        // replace the outher exchange with the content from the downloaded file
                        exchange.getIn().setBody(result.getIn().getBody());

                        // stop the consumer
                        consumer.stop();
                    }
                }).to("mock:result");
                // END SNIPPET: e2
            }
        };
    }
}