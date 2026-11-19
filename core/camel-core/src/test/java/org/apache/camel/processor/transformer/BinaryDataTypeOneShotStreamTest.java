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
package org.apache.camel.processor.transformer;

import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * The binary data type reads a stream that is not a stream cache into a byte array, so a stream that can be read only
 * once is still there for the steps after it.
 */
public class BinaryDataTypeOneShotStreamTest extends ContextTestSupport {

    @Test
    public void oneShotStreamIsStillReadableAfterBinary() throws Exception {
        getMockEndpoint("mock:out").expectedBodiesReceived("payload");
        getMockEndpoint("mock:out").expectedHeaderReceived("Content-Type", "application/octet-stream");

        template.sendBody("direct:start", "x");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").noStreamCaching()
                        // a stream that can be read only once (no mark/reset), as a bean or a component can give
                        .process(e -> e.getMessage().setBody(new SequenceInputStream(
                                Collections.enumeration(
                                        List.of(new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)))))))
                        .transformDataType("application-octet-stream")
                        .log("${body}")
                        .convertBodyTo(String.class)
                        .to("mock:out");
            }
        };
    }
}
