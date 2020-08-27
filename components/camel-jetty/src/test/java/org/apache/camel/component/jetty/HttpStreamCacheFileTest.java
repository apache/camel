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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpStreamCacheFileTest extends BaseJettyTest {

    private final String responseBody = "12345678901234567890123456789012345678901234567890";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
        super.setUp();
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfResponse() throws Exception {
        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        // the temporary files should have been deleted
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals(0, files.length, "There should be no files");
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfException() throws Exception {
        try {
            template.requestBody("direct:start", null, String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException hofe = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            String s = context.getTypeConverter().convertTo(String.class, hofe.getResponseBody());
            assertEquals(responseBody, s, "Response body");
        }

        // the temporary files should have been deleted
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals(0, files.length, "There should be no files");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable stream caching and use a low threshold so its forced
                // to write to file
                context.getStreamCachingStrategy().setSpoolThreshold(16);
                context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
                context.setStreamCaching(true);

                // use a route so we got an unit of work
                from("direct:start").to("http://localhost:{{port}}/myserver");

                from("jetty://http://localhost:{{port}}/myserver").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if (ObjectHelper.isEmpty(body)) {
                            exchange.getMessage().setBody(responseBody);
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                        } else {
                            exchange.getMessage().setBody("Bye World");
                        }
                    }
                });
            }
        };
    }

}
