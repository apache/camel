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
package org.apache.camel.component.stream;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test when using custom output stream.
 */
public class StreamHeaderTest extends CamelTestSupport {

    // START SNIPPET: e1
    private OutputStream mystream = new MyOutputStream();
    private StringBuilder sb = new StringBuilder();

    @Test
    public void testStringContent() {
        template.sendBody("direct:in", "Hello");
        // StreamProducer appends \n in text mode
        assertEquals("Hello" + LS, sb.toString());
    }

    @Test
    public void testBinaryContent() {
        template.sendBody("direct:in", "Hello".getBytes());
        // StreamProducer is in binary mode so no \n is appended
        assertEquals("Hello", sb.toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").setHeader("stream", constant(mystream)).
                    to("stream:header");
            }
        };
    }

    private class MyOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            sb.append((char)b);
        }
    }
    // END SNIPPET: e1

}
