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
package org.apache.camel.component.xquery;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class XQueryLanguageFromFileTest extends CamelTestSupport {

    @Test
    public void testXQueryFromFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:davsclaus");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hello World");

        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedMessageCount(1);
        other.message(0).body(String.class).contains("Bye World");

        template.sendBodyAndHeader("file:target/xquery", "<mail from=\"davsclaus@apache.org\"><subject>Hey</subject><body>Hello World!</body></mail>",
                Exchange.FILE_NAME, "claus.xml");

        template.sendBodyAndHeader("file:target/xquery", "<mail from=\"janstey@apache.org\"><subject>Hey</subject><body>Bye World!</body></mail>",
                Exchange.FILE_NAME, "janstey.xml");

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/xquery");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/xquery")
                    .choice()
                        .when().xquery("/mail/@from = 'davsclaus@apache.org'")
                            .convertBodyTo(String.class)
                            .to("mock:davsclaus")
                        .otherwise()
                            .convertBodyTo(String.class)
                            .to("mock:other");
            }
        };
    }
}
