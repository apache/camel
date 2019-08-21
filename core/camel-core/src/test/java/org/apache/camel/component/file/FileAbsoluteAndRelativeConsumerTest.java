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
import org.junit.Before;
import org.junit.Test;

public class FileAbsoluteAndRelativeConsumerTest extends ContextTestSupport {

    private String base;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filerelative");
        deleteDirectory("target/data/fileabsolute");
        // use current dir as base as aboslute path
        base = new File("").getAbsolutePath() + "/target/data/fileabsolute";
        super.setUp();
    }

    @Test
    public void testRelative() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:relative");
        mock.expectedMessageCount(1);

        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("test" + File.separator + "hello.txt");
        mock.message(0).header(Exchange.FILE_NAME_ONLY).isEqualTo("hello.txt");

        template.sendBodyAndHeader("file://target/data/filerelative", "Hello World", Exchange.FILE_NAME, "test/hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAbsolute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:absolute");
        mock.expectedMessageCount(1);

        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("test" + File.separator + "hello.txt");
        mock.message(0).header(Exchange.FILE_NAME_ONLY).isEqualTo("hello.txt");

        template.sendBodyAndHeader("file://target/data/fileabsolute", "Hello World", Exchange.FILE_NAME, "test/hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filerelative?initialDelay=0&delay=10&recursive=true").convertBodyTo(String.class).to("mock:relative");

                from("file://" + base + "?initialDelay=0&delay=10&recursive=true").convertBodyTo(String.class).to("mock:absolute");
            }
        };
    }
}
