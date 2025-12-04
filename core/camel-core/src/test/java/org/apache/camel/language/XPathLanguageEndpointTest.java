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

package org.apache.camel.language;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XPathLanguageEndpointTest extends ContextTestSupport {

    @Test
    public void testXPath() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        assertMockEndpointsSatisfied();

        // test converter also works with shorthand names
        QName qn = context.getTypeConverter().convertTo(QName.class, "NODESET");
        Assertions.assertEquals(XPathConstants.NODESET, qn);
        qn = context.getTypeConverter().convertTo(QName.class, "nodeset");
        Assertions.assertEquals(XPathConstants.NODESET, qn);
        qn = context.getTypeConverter().convertTo(QName.class, "BOOLEAN");
        Assertions.assertEquals(XPathConstants.BOOLEAN, qn);
        qn = context.getTypeConverter().convertTo(QName.class, "boolean");
        Assertions.assertEquals(XPathConstants.BOOLEAN, qn);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader(Exchange.LANGUAGE_SCRIPT, constant("/foo/text()"))
                        .to("language:xpath?allowTemplateFromHeader=true")
                        .to("mock:result");
            }
        };
    }
}
