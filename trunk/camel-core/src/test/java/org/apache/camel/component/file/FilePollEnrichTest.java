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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FilePollEnrichTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/pollenrich");
        super.setUp();
    }

    public void testFilePollEnrich() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedFileExists("target/pollenrich/done/hello.txt");

        template.sendBodyAndHeader("file:target/pollenrich", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // file should be moved
        File file = new File("target/pollenrich/hello.txt");
        assertFalse("File should have been moved", file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=1000").routeId("foo")
                    .log("Trigger timer foo")
                    .pollEnrich("file:target/pollenrich?move=done", 5000)
                    .convertBodyTo(String.class)
                    .log("Polled filed ${file:name}")
                    .to("mock:result")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // force stop route after use to prevent firing timer again
                            exchange.getContext().stopRoute("foo", 100, TimeUnit.MILLISECONDS);
                        }
                    });
            }
        };
    }
}
