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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
public class NettyHttpCompressTest extends BaseNettyTestSupport {

    @Test
    public void testContentType() throws Exception {
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> headers = new HashMap<>();
        headers.put("content-type", "text/plain; charset=\"UTF-8\"");
        headers.put("Accept-Encoding", "compress, gzip");
        byte[] out
                = template.requestBodyAndHeaders("netty-http:http://localhost:{{port}}/foo", data,
                        headers, byte[].class);
        // deflate the zip
        GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(out));
        String s = IOHelper.loadText(zis);
        IOHelper.close(zis);
        // The decoded out has some space to clean up.
        assertEquals("Bye World", s.trim());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?compression=true")
                        .transform().constant("Bye World").setHeader("content-type").constant("text/plain; charset=\"UTF-8\"");
            }
        };
    }
}
