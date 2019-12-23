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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("Manual test")
public class FileProducerAppendManyMessagesFastTest extends ContextTestSupport {

    private boolean enabled;

    @Override
    @Before
    public void setUp() throws Exception {
        if (!enabled) {
            return;
        }

        deleteDirectory("target/data/big");
        createDirectory("target/data/big");
        deleteDirectory("target/data/out");
        createDirectory("target/data/out");

        // create a big file
        File file = new File("target/data/big/data.txt");
        FileOutputStream fos = new FileOutputStream(file);
        for (int i = 0; i < 100000; i++) {
            String s = "Hello World this is a long line with number " + i + LS;
            fos.write(s.getBytes());
        }
        fos.close();

        super.setUp();
    }

    @Test
    public void testBigFile() throws Exception {
        if (!enabled) {
            return;
        }

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
                from("file:target/data/big").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // store a output stream we use for writing
                        FileOutputStream fos = new FileOutputStream("target/data/out/also-big.txt", true);
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
