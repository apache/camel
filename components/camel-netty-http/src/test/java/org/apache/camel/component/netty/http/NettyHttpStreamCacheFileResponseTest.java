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
package org.apache.camel.component.netty.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpStreamCacheFileResponseTest extends BaseNettyTestSupport {

    private final String body = "12345678901234567890123456789012345678901234567890";
    private final String body2 = "Bye " + body;

    @Override
    public void doPreSetup() throws Exception {
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfResponse() {
        NotifyBuilder builder = new NotifyBuilder(context).whenDone(1).create();

        String out = template.requestBody("http://localhost:{{port}}/myserver", body, String.class);
        assertEquals(body2, out);

        assertTrue(builder.matches(5, TimeUnit.SECONDS));

        // the temporary files should have been deleted
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals(0, files.length, "There should be no files");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // enable stream caching and use a low threshold so its forced to write to file
                context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
                context.getStreamCachingStrategy().setSpoolThreshold(16);
                context.setStreamCaching(true);

                from("netty-http:http://localhost:{{port}}/myserver")
                        // wrap the response in 2 input streams so it will force caching to disk
                        .transform().constant(new BufferedInputStream(new ByteArrayInputStream(body2.getBytes())))
                        .to("log:reply");
            }
        };
    }

}
