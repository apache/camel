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
package org.apache.camel.component.xslt.saxon;

import java.util.List;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltSaxonJsonBodyTest extends CamelTestSupport {

    private static final String JSON_INPUT = """
            {
                "name": "John Doe",
                "age": 30,
                "email": "john.doe@example.com",
                "address": {
                    "street": "123 Main St",
                    "city": "New York",
                    "zipcode": "10001"
                },
                "hobbies": ["reading", "cycling", "swimming"]
            }
            """;

    @Test
    public void testJsonBodyTransformation() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:json-result");
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:json-start", JSON_INPUT);

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Document res = exchange.getIn().getBody(Document.class);

        String expected = """
                <Person>
                    <Name>John Doe</Name>
                    <Age>30</Age>
                    <Email>john.doe@example.com</Email>
                    <Address>
                        <Street>123 Main St</Street>
                        <City>New York</City>
                        <Zipcode>10001</Zipcode>
                    </Address>
                    <Hobbies>
                        <Hobby>reading</Hobby>
                        <Hobby>cycling</Hobby>
                        <Hobby>swimming</Hobby>
                    </Hobbies>
                </Person>
                """;
        XmlAssert.assertThat(res)
                .and(expected)
                .ignoreWhitespace()
                .ignoreComments()
                .areIdentical();
    }

    @Test
    public void testJsonBodyDisabled() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:no-json-result");
        endpoint.expectedMessageCount(1);

        try {
            template.sendBody("direct:no-json-start", JSON_INPUT);
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("Content is not allowed in prolog"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:json-start")
                        .to("xslt-saxon:org/apache/camel/component/xslt/saxon/json-transform.xsl?useJsonBody=true")
                        .to("mock:json-result");

                from("direct:no-json-start")
                        .to("xslt-saxon:org/apache/camel/component/xslt/saxon/json-transform.xsl")
                        .to("mock:no-json-result");
            }
        };
    }
}
