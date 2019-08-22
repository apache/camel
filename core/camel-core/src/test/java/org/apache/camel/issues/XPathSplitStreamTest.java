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
package org.apache.camel.issues;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.xpath.XPathBuilder;
import org.junit.Before;
import org.junit.Test;

public class XPathSplitStreamTest extends ContextTestSupport {

    private static int size = 100;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/file/xpathsplit");
        super.setUp();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("\n<persons>");
        for (int i = 0; i < size; i++) {
            sb.append("\n<person><id>" + i + "</id><name>John Doe</name></person>");
        }
        sb.append("\n</persons>");

        template.sendBodyAndHeader("file://target/data/file/xpathsplit", sb.toString(), Exchange.FILE_NAME, "bigfile.xml");
    }

    @Test
    public void testXPathSplitStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:splitted");
        mock.expectedMessageCount(size);
        mock.expectsNoDuplicates().body();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XPathBuilder personXPath = XPathBuilder.xpath("/persons/person").documentType(InputSource.class);

                // START SNIPPET: e1
                from("file://target/data/file/xpathsplit?initialDelay=0&delay=10")
                    // set documentType to org.xml.sax.InputSource then Camel
                    // will use SAX to split the file
                    .split(personXPath).streaming().to("mock:splitted");
                // END SNIPPET: e1
            }
        };
    }
}
