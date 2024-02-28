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
package org.apache.camel.component.jetty.file;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JettyHttpFileCacheTest extends CamelTestSupport {
    private static final String TEST_STRING = "This is a test string and it has enough"
                                              + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        context.getStreamCachingStrategy().setSpoolThreshold(16);
        context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
    }

    @Test
    void testJettyHttpFileCache() throws Exception {

        String response = template.requestBody("http://localhost:8201/clipboard/download/file", "   ", String.class);
        assertEquals(TEST_STRING, response, "should get the right response");

        File file = new File("target/cachedir");
        String[] files = file.list();
        assertNotNull(files);
        assertEquals(0, files.length, "There should not have any temp file");

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("jetty:http://localhost:8201/clipboard/download?chunked=true&matchOnUriPrefix=true")
                        .to("http://localhost:9101?bridgeEndpoint=true");

                from("jetty:http://localhost:9101?chunked=true&matchOnUriPrefix=true")
                        .process(exchange -> exchange.getMessage().setBody(TEST_STRING));
            }
        };
    }

}
