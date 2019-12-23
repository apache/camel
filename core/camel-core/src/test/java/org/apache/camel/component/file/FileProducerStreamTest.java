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
package org.apache.camel.component.file;

import java.io.File;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class FileProducerStreamTest extends ContextTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        super.setUp();
    }

    @Test
    public void testStream() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Object body = Stream.of("ABC", "DEF", "1234567890");
        template.sendBodyAndHeader("direct:start", body, Exchange.FILE_NAME, "report.xt");

        assertMockEndpointsSatisfied();

        File file = new File("target/stream/report.xt");
        String text = context.getTypeConverter().convertTo(String.class, file);
        assertEquals("ABCDEF1234567890", text);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("file:target/stream").to("mock:result");
            }
        };
    }
}
