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
package org.apache.camel.processor;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StreamCachingVariableTest extends ContextTestSupport {

    private static final String TEST_FILE = "src/test/resources/org/apache/camel/util/foo.txt";

    @Test
    public void testStreamCaching() throws Exception {
        // exchange scoped
        getMockEndpoint("mock:result").expectedBodiesReceived("foo");
        File file = new File(TEST_FILE);
        FileInputStreamCache cache = new FileInputStreamCache(file);
        template.sendBody("direct:start", cache);
        assertMockEndpointsSatisfied();

        // global scoped
        cache = new FileInputStreamCache(file);
        context.setVariable("myKey2", cache);

        String data = context.getVariable("myKey2", String.class);
        Assertions.assertEquals("foo", data);
        data = context.getVariable("myKey2", String.class);
        Assertions.assertEquals("foo", data);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setVariable("myKey", simple("${body}"))
                        .process(e -> {
                            String data = e.getVariable("myKey", String.class);
                            Assertions.assertEquals("foo", data);
                        })
                        .process(e -> {
                            String data = e.getVariable("myKey", String.class);
                            Assertions.assertEquals("foo", data);
                        })
                        .to("mock:result");
            }
        };
    }
}
