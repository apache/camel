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
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test based on user forum question
 */
public class FromQueueThenConsumeFtpToMockTest extends FtpServerTestSupport {

    private int port = 20034;
    private String storeUrl = "ftp://admin@localhost:" + port + "/getme?password=admin&binary=false";

    // START SNIPPET: e1
    // we use directory=false to indicate we only want to consume a single file
    // we use delay=5000 to use 5 sec delay between pools to avoid polling a second time before we stop the consumer
    // this is because we only want to run a single poll and get the file
    // file=getme/ is the path to the folder where the file is
    private String getUrl = "ftp://admin@localhost:" + port + "?password=admin&binary=false&directory=false&consumer.delay=5000&file=getme/";
    // END SNIPPET: e1

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("./res/home/getme");
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want to unit
        // test that we can pool once
        Endpoint endpoint = context.getEndpoint(storeUrl);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Bye World");
        exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testFromQueueThenConsumeFtp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("seda:start", "Hello World", "myfile", "hello.txt");

        assertMockEndpointsSatisfied();
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
                        String url = getUrl + filename;

                        // create a ftp endpoint
                        Endpoint ftp = context.getEndpoint(url);

                        // create a polling consumer so we can poll the remote ftp file
                        PollingConsumer consumer = ftp.createPollingConsumer();
                        consumer.start();
                        // receive the remote ftp without timeout
                        Exchange result = consumer.receive();
                        // we must stop the consumer
                        consumer.stop();

                        // the result is the response from the FTP consumer (the downloaded file)
                        // replace the outher exchange with the content from the downloaded file
                        exchange.getIn().setBody(result.getIn().getBody());
                    }
                }).to("mock:result");
                // END SNIPPET: e2
            }
        };
    }

}