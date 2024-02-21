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
package org.apache.camel.builder.endpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpRawParameterTest extends BaseEndpointDslTest {

    @Test
    public void testRaw() throws Exception {
        FtpEndpoint ftp = (FtpEndpoint) context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("ftp"))
                .findFirst().get();
        assertNotNull(ftp);
        assertEquals(5000L, ftp.getDelay());
        assertTrue(ftp.getConfiguration().isBinary());
        assertEquals("scott", ftp.getConfiguration().getUsername());
        assertEquals("sec+%ret", ftp.getConfiguration().getPassword());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            public void configure() throws Exception {
                from(ftp("localhost:2121/inbox").username("scott").password("RAW(sec+%ret)").binary(true).delay(5000))
                        .routeId("myroute").noAutoStartup()
                        .convertBodyTo(String.class)
                        .to(mock("result"));
            }
        };
    }

}
