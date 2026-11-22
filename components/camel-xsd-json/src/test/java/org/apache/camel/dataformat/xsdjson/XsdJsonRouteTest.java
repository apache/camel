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
package org.apache.camel.dataformat.xsdjson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XsdJsonRouteTest extends CamelTestSupport {

    private static final String RESPONSE = "<getOrderResponse xmlns=\"http://example.com/orders\"><order>"
                                           + "<orderId>42</orderId><customer>Ann</customer><status>NEW</status>"
                                           + "<item><sku>A</sku><quantity>2</quantity><price>9.95</price></item>"
                                           + "</order></getOrderResponse>";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:toJson")
                        .unmarshal().xsdJson("classpath:xsdjson/orders.wsdl");

                from("direct:toXml")
                        .marshal(dataFormat().xsdJson().schema("classpath:xsdjson/orders.wsdl").element("getOrder").end())
                        .convertBodyTo(String.class);

                from("direct:toJsonWithRoot")
                        .unmarshal(dataFormat().xsdJson().schema("classpath:xsdjson/orders.wsdl").element("getOrderResponse")
                                .includeRoot(true).end());

                from("direct:toXmlByHeader")
                        .marshal(dataFormat().xsdJson().schema("classpath:xsdjson/orders.wsdl").end())
                        .convertBodyTo(String.class);
            }
        };
    }

    @Test
    void javaDsl() {
        assertThat(template.requestBody("direct:toJson", RESPONSE, String.class))
                .isEqualTo("{\"order\":{\"orderId\":\"42\",\"customer\":\"Ann\",\"status\":\"NEW\","
                           + "\"item\":[{\"sku\":\"A\",\"quantity\":2,\"price\":9.95}]}}");
        assertThat(template.requestBody("direct:toXml", "{\"orderId\":\"42\"}", String.class))
                .endsWith("<tns:getOrder xmlns:tns=\"http://example.com/orders\"><tns:orderId>42</tns:orderId></tns:getOrder>");
    }

    @Test
    void includeRootAndElementHeader() {
        assertThat(template.requestBody("direct:toJsonWithRoot", RESPONSE, String.class))
                .isEqualTo("{\"getOrderResponse\":{\"order\":{\"orderId\":\"42\",\"customer\":\"Ann\",\"status\":\"NEW\","
                           + "\"item\":[{\"sku\":\"A\",\"quantity\":2,\"price\":9.95}]}}}");
        assertThat(template.requestBodyAndHeader("direct:toXmlByHeader", "{\"orderId\": \"42\"}",
                XsdJsonDataFormat.ELEMENT, "getOrder", String.class))
                .endsWith("<tns:getOrder xmlns:tns=\"http://example.com/orders\"><tns:orderId>42</tns:orderId></tns:getOrder>");
    }
}
