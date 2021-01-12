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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerPreMoveDeleteTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/premove");
        super.setUp();
    }

    @Test
    public void testPreMoveDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/data/premove", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        File pre = new File("target/data/premove/work/hello.txt");
        assertFalse(pre.exists(), "Pre move file should have been deleted");
    }

    @Test
    public void testPreMoveDeleteSameFileTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/data/premove", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        // reset and drop the same file again
        mock.reset();
        oneExchangeDone.reset();
        mock.expectedBodiesReceived("Hello Again World");

        template.sendBodyAndHeader("file://target/data/premove", "Hello Again World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        File pre = new File("target/data/premove/work/hello.txt");
        assertFalse(pre.exists(), "Pre move file should have been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/premove?preMove=work&delete=true&idempotent=false&initialDelay=0&delay=10")
                        .process(new MyPreMoveCheckerProcessor()).to("mock:result");
            }
        };
    }

    public static class MyPreMoveCheckerProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            File pre = new File("target/data/premove/work/hello.txt");
            assertTrue(pre.exists(), "Pre move file should exist");
        }
    }
}
