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
package org.apache.camel.component.stream;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for stream file
 */
public class StreamFileTest extends CamelTestSupport {

    private FileOutputStream fos;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        createDirectory("target/stream");

        File file = new File("target/stream/streamfile.txt");
        file.createNewFile();

        fos = new FileOutputStream(file);
        fos.write("Hello\n".getBytes());

        super.setUp();
    }

    @Test
    public void testFile() throws Exception {
        context.start();

        try {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Hello");

            // can not use route builder as we need to have the file created in the setup before route builder starts
            Endpoint endpoint = context.getEndpoint("stream:file?fileName=target/stream/streamfile.txt&delay=100");
            Consumer consumer = endpoint.createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    template.send("mock:result", exchange);
                }
            });
            consumer.start();

            assertMockEndpointsSatisfied();

            consumer.stop();
        } finally {
            fos.close();
        }
    }

    @Test
    public void testFileProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("produce")
                    .to("stream:file?fileName=target/stream/StreamFileTest.txt&autoCloseCount=2");
                from("file://target/stream?fileName=StreamFileTest.txt&noop=true").routeId("consume").autoStartup(false)
                    .split().tokenize(LS).to("mock:result");
            }
        });
        context.start();

        template.sendBody("direct:start", "Hadrian");
        template.sendBody("direct:start", "Apache");
        template.sendBody("direct:start", "Camel");
        
        context.startRoute("consume");
        assertMockEndpointsSatisfied();
        context.stop();
    }
}