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
package org.apache.camel.component.stream;

import java.io.ByteArrayOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stream.mock.MockURLConnection;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for producer writing to URL.
 */
public class StreamToUrlTest extends CamelTestSupport {

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    String message = "message";

    String existingHandlers = System.getProperty("java.protocol.handler.pkgs");

    @Override
    protected void doPreSetup() throws Exception {
        System.setProperty("java.protocol.handler.pkgs", getClass().getPackage().getName());
        MockURLConnection.setOutputStream(buffer);
    }

    @Override
    public void tearDown() throws Exception {
        if (existingHandlers != null) {
            System.setProperty("java.protocol.handler.pkgs", existingHandlers);
        }
        super.tearDown();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("stream:url?url=mock:");
            }
        };
    }

    @Test
    public void shouldSendToUrlOutputStream() throws Exception {
        // When
        template.sendBody("direct:start", message);

        // Then
        String messageReceived = new String(buffer.toByteArray()).trim();
        assertEquals(message, messageReceived);
    }

}
