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
package org.apache.camel.component.file.stress;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 */
@Disabled("Manual test")
public class FileProducerAppendManyMessagesFastManualTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // create a big file
        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(testFile("big/data.txt")))) {
            for (int i = 0; i < 1000; i++) {
                String s = "Hello World this is a long line with number " + i + LS;
                fos.write(s.getBytes());
            }
        }
    }

    @Test
    public void testBigFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(2 * 60000);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("big?initialDelay=1000")).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // store a output stream we use for writing
                        FileOutputStream fos = new FileOutputStream(testFile("out/also-big.txt").toFile(), true);
                        exchange.setProperty("myStream", fos);
                    }
                }).split(body().tokenize(LS)).streaming().to("log:processing?groupSize=1000").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        OutputStream fos = exchange.getProperty("myStream", OutputStream.class);
                        byte[] data = exchange.getIn().getBody(byte[].class);
                        fos.write(data);
                        fos.write(LS.getBytes());
                    }
                }).end().process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        OutputStream fos = exchange.getProperty("myStream", OutputStream.class);
                        fos.close();
                        exchange.removeProperty("myStream");
                    }
                }).to("mock:done");
            }
        };
    }
}
