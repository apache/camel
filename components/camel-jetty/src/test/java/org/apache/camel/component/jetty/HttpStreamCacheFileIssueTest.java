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
package org.apache.camel.component.jetty;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class HttpStreamCacheFileIssueTest extends BaseJettyTest {

    private String body = "12345678901234567890123456789012345678901234567890";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
        super.setUp();
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfStop() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals(body, out);

        // the temporary files should have been deleted
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals("There should be no files", 0, files.length);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable stream caching and use a low threshold so its forced to write to file
                context.getProperties().put(CachedOutputStream.TEMP_DIR, "target/cachedir");
                context.getProperties().put(CachedOutputStream.THRESHOLD, "16");
                context.setStreamCaching(true);

                // use a route so we got an unit of work
                from("direct:start")
                    .to("http://localhost:{{port}}/myserver")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // there should be a temp cache file
                            File file = new File("target/cachedir");
                            String[] files = file.list();
                            assertTrue("There should be a temp cache file", files.length > 0);
                        }
                    })
                    // TODO: CAMEL-3839: need to convert the body to a String as the tmp file will be deleted
                    // before the producer template can convert the result back
                    .convertBodyTo(String.class)
                    .to("mock:result");

                from("jetty://http://localhost:{{port}}/myserver")
                    .transform().constant(body);
            }
        };
    }

}
