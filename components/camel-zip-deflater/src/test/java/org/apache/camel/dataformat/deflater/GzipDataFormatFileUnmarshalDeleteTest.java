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
package org.apache.camel.dataformat.deflater;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GzipDataFormatFileUnmarshalDeleteTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/gzip");
        super.setUp();
    }

    @Test
    public void testGzipFileUnmarshalDelete() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/data/gzip", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        notify.matchesWaitTime();

        File in = new File("target/gzip/hello.txt");
        assertFalse(in.exists(), "Should have been deleted " + in);

        File out = new File("target/gzip/out/hello.txt.gz");
        assertFalse(out.exists(), "Should have been deleted " + out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/gzip?initialDelay=0&delay=10&delete=true")
                        .marshal().gzipDeflater()
                        .to("file:target/data/gzip/out?fileName=${file:name}.gz");

                from("file:target/data/gzip/out?initialDelay=0&delay=10&delete=true")
                        .unmarshal().gzipDeflater()
                        .to("mock:result");
            }
        };
    }
}
