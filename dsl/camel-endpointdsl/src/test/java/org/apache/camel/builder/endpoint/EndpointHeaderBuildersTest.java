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

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointHeaderBuildersTest extends BaseEndpointDslTest {

    private static final String OUTPUT_DIR = "target/endpoint-header-builders-test";
    private static final String FILE_NAME = "hello.txt";

    @Test
    public void testHeaderBuildersFromEndpointRouteBuilder() throws Exception {
        template.sendBody("direct:a", "Hello World");
        assertTrue(Files.exists(Paths.get(OUTPUT_DIR, FILE_NAME)));
    }

    @Test
    public void testStaticHeaderBuilders() {
        assertNotNull(EndpointHeaderBuilders.file());
        assertEquals("CamelFileName", EndpointHeaderBuilders.file().fileName());
        assertEquals("CamelFileLength", EndpointHeaderBuilders.file().fileLength());
    }

    @Test
    public void testHeaderBuildersReturnCorrectNames() {
        assertEquals("CamelTimerFiredTime", EndpointHeaderBuilders.timer().timerFiredTime());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .setHeader(headers().file().fileName(), constant(FILE_NAME))
                        .to("file:" + OUTPUT_DIR);
            }
        };
    }
}
