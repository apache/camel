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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerNoopTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/filenoop");
        super.setUp();
    }

    @Test
    public void testNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("file://target/data/filenoop", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/filenoop", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        assertTrue(new File("target/data/filenoop/hello.txt").exists());
        assertTrue(new File("target/data/filenoop/bye.txt").exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filenoop?noop=true&initialDelay=0&delay=10").convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
