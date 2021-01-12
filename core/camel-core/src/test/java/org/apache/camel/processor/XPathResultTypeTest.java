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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class XPathResultTypeTest extends ContextTestSupport {

    @Test
    public void xpathLongAndObject() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n"
                     + "<Books count=\"2\">\n"
                     + "  <Book Id=\"1\" ISBN=\"1\"><Titel>First</Titel></Book>\n"
                     + "  <Book Id=\"2\" ISBN=\"2\"><Titel>SECOND</Titel></Book>\n"
                     + "</Books>";

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).exchangeProperty("BOOK_COUNT").isEqualTo(2);
        getMockEndpoint("mock:split").expectedMessageCount(2);
        getMockEndpoint("mock:split").message(0).body(String.class).contains("First");
        getMockEndpoint("mock:split").message(1).body(String.class).contains("SECOND");

        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setProperty("BOOK_COUNT", xpath( "//Books/@count", Long.class))
                    .split(xpath("//Books/Book"))
                        .to("mock:split")
                    .end()
                    .to("mock:result");
            }
        };
    }

}
