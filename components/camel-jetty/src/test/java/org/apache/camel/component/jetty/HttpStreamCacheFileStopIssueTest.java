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
package org.apache.camel.component.jetty;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpStreamCacheFileStopIssueTest extends BaseJettyTest {

    private final String body = "12345678901234567890123456789012345678901234567890";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
        super.setUp();
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfStop() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals(body, out);

        // the temporary files should have been deleted
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals(0, files.length, "There should be no files");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // enable stream caching and use a low threshold so its forced
                // to write to file
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(16);
                context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
                context.setStreamCaching(true);

                // use a route so we got an unit of work
                from("direct:start").to("http://localhost:{{port}}/myserver").process(new Processor() {
                    public void process(Exchange exchange) {
                        // there should be a temp cache file
                        File file = new File("target/cachedir");
                        String[] files = file.list();
                        assertTrue(files.length > 0, "There should be a temp cache file");
                    }
                })
                        // TODO: CAMEL-3839: need to convert the body to a String as
                        // the tmp file will be deleted
                        // before the producer template can convert the result back
                        .convertBodyTo(String.class)
                        // mark the exchange to stop continue routing
                        .stop().to("mock:result");

                from("jetty://http://localhost:{{port}}/myserver").transform().constant(body);
            }
        };
    }

}
