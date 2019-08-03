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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for System.out
 */
public class StreamSystemOutTest extends CamelTestSupport {

    String message = "Hello World";

    PrintStream stdOut = System.out;

    ByteArrayOutputStream mockOut = new ByteArrayOutputStream();

    // START SNIPPET: e1
    @Test
    public void testStringContent() throws Exception {
        try {
            // Given
            System.setOut(new PrintStream(mockOut));

            // When
            template.sendBody("direct:in", message);

            // Then
            assertEquals(message + LS, new String(mockOut.toByteArray()));
        } finally {
            System.setOut(stdOut);
        }
    }

    @Test
    public void testBinaryContent() {
        try {
            // Given
            System.setOut(new PrintStream(mockOut));

            // When
            template.sendBody("direct:in", message.getBytes());

            // Then
            assertEquals(message, new String(mockOut.toByteArray()));
        } finally {
            System.setOut(stdOut);
        }
    }

    @Test
    public void shouldSkipNullBody() {
        try {
            // Given
            System.setOut(new PrintStream(mockOut));

            // When
            template.sendBody("direct:in", null);

            // Then
            assertEquals(0, mockOut.toByteArray().length);
        } finally {
            System.setOut(stdOut);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("stream:out");
            }
        };
    }
    // END SNIPPET: e1

}
