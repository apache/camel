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
package org.apache.camel.impl;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ZipDataFormatFileUnmarshalDeleteTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/zip");
        super.setUp();
    }

    public void testZipFileUnmarshalDelete() throws Exception {
        // there are 2 exchanges
        NotifyBuilder event = event().whenDone(2).create();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBodyAndHeader("file:target/zip", "Hello World", Exchange.FILE_NAME, "hello.txt");
        assertMockEndpointsSatisfied();

        event.matchesMockWaitTime();

        File in = new File("target/zip/hello.txt");
        assertFalse("Should have been deleted " + in, in.exists());

        File out = new File("target/zip/out/hello.txt.zip");
        assertFalse("Should have been deleted " + out, out.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/zip?initialDelay=0&delay=10&delete=true")
                    .marshal().zip()
                    .to("file:target/zip/out?fileName=${file:name}.zip");

                from("file:target/zip/out?initialDelay=0&delay=10&delete=true")
                    .unmarshal().zip()
                    .to("mock:result");
            }
        };
    }
}