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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class FileLanguageCBRTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/cbr");
        super.setUp();
    }

    @Test
    public void testTxt() throws Exception {
        getMockEndpoint("mock:txt").expectedMessageCount(1);
        getMockEndpoint("mock:dat").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("file://target/data/cbr", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDat() throws Exception {
        getMockEndpoint("mock:txt").expectedMessageCount(0);
        getMockEndpoint("mock:dat").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("file://target/data/cbr", "Bye World", Exchange.FILE_NAME, "bye.dat");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOther() throws Exception {
        getMockEndpoint("mock:txt").expectedMessageCount(0);
        getMockEndpoint("mock:dat").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/data/cbr", "Hi World", Exchange.FILE_NAME, "hi.foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/cbr?delete=true&initialDelay=0&delay=10").convertBodyTo(String.class).choice().when().simple("${file:ext} == 'txt'").to("mock:txt").when()
                    .simple("${file:ext} == 'dat'").to("mock:dat").otherwise().to("mock:other");
            }
        };
    }
}
