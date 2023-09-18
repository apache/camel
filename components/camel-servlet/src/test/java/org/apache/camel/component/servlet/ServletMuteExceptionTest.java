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
package org.apache.camel.component.servlet;

import java.io.ByteArrayInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServletMuteExceptionTest extends ServletCamelRouterTestSupport {

    @Test
    public void testMuteDefaultTrue() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/muteDefault",
                new ByteArrayInputStream("".getBytes()), "text/plain");
        WebResponse response = query(req, false);

        assertEquals(500, response.getResponseCode());
        assertEquals("text/plain", response.getContentType());
        assertTrue(response.getText().isEmpty());
    }

    @Test
    public void testMuteException() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/mute",
                new ByteArrayInputStream("".getBytes()), "text/plain");
        WebResponse response = query(req, false);

        assertEquals(500, response.getResponseCode());
        assertEquals("text/plain", response.getContentType());
        assertTrue(response.getText().isEmpty());
    }

    @Test
    public void testMuteWithTransferException() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/muteWithTransfer",
                new ByteArrayInputStream("".getBytes()), "text/plain");
        WebResponse response = query(req, false);

        assertEquals(500, response.getResponseCode());
        assertEquals("text/plain", response.getContentType());
        assertTrue(response.getText().isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("servlet:muteDefault")
                        .throwException(new IllegalArgumentException("Damn"));

                from("servlet:mute?muteException=true")
                        .throwException(new IllegalArgumentException("Damn"));

                from("servlet:muteWithTransfer?muteException=true&transferException=true")
                        .throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
