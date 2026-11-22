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

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inline types of a WSDL, as for the payload of a SOAP service in camel-cxf PAYLOAD mode.
 */
class XsdJsonWsdlTest extends XsdJsonTestSupport {

    private static final String WSDL = "classpath:xsdjson/orders.wsdl";

    @Test
    void responseWithOneAndTwoItems() throws Exception {
        XsdJsonDataFormat df = dataFormat(WSDL, d -> {
        });
        String one = """
                <getOrderResponse xmlns="http://example.com/orders"><order>
                  <orderId>42</orderId><customer>Ann</customer><status>SHIPPED</status>
                  <item><sku>A</sku><quantity>2</quantity><price>9.95</price></item>
                </order></getOrderResponse>""";
        // one item is an array as well, and quantity and price are numbers
        assertThat(toJson(df, one)).isEqualTo("{\"order\":{\"orderId\":\"42\",\"customer\":\"Ann\",\"status\":\"SHIPPED\","
                                              + "\"item\":[{\"sku\":\"A\",\"quantity\":2,\"price\":9.95}]}}");
        String two = """
                <tns:getOrderResponse xmlns:tns="http://example.com/orders"><tns:order>
                  <tns:orderId>42</tns:orderId><tns:customer>Ann</tns:customer><tns:status>SHIPPED</tns:status>
                  <tns:item><tns:sku>A</tns:sku><tns:quantity>2</tns:quantity><tns:price>9.95</tns:price></tns:item>
                  <tns:item><tns:sku>B</tns:sku><tns:quantity>1</tns:quantity><tns:price>100</tns:price></tns:item>
                </tns:order></tns:getOrderResponse>""";
        assertThat(toJson(df, two)).isEqualTo("{\"order\":{\"orderId\":\"42\",\"customer\":\"Ann\",\"status\":\"SHIPPED\","
                                              + "\"item\":[{\"sku\":\"A\",\"quantity\":2,\"price\":9.95},"
                                              + "{\"sku\":\"B\",\"quantity\":1,\"price\":100}]}}");
    }

    @Test
    void requestInSchemaOrderWithTheWsdlPrefix() throws Exception {
        XsdJsonDataFormat df = dataFormat(WSDL, d -> d.setElement("placeOrder"));
        String xml = toXml(df, "{\"item\":[{\"price\":9.95,\"quantity\":2,\"sku\":\"A\"}],\"customer\":\"Ann\"}");
        // the prefix tns is the one wsdl:definitions declares, inherited by the inline schema
        assertThat(xml).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                  + "<tns:placeOrder xmlns:tns=\"http://example.com/orders\">"
                                  + "<tns:customer>Ann</tns:customer>"
                                  + "<tns:item><tns:sku>A</tns:sku><tns:quantity>2</tns:quantity><tns:price>9.95</tns:price></tns:item>"
                                  + "</tns:placeOrder>");
    }

    @Test
    void elementPerMessageFromTheHeader() throws Exception {
        XsdJsonDataFormat df = dataFormat(WSDL, d -> d.setElement("getOrder"));
        assertThat(toXml(df, "{\"orderId\":\"42\"}")).endsWith(
                "<tns:getOrder xmlns:tns=\"http://example.com/orders\"><tns:orderId>42</tns:orderId></tns:getOrder>");

        // the fault of the operation
        Exchange exchange = exchange();
        exchange.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, "{http://example.com/orders}orderNotFound");
        assertThat(toXml(df, exchange, "{\"orderId\":\"43\"}")).endsWith(
                "<tns:orderNotFound xmlns:tns=\"http://example.com/orders\"><tns:orderId>43</tns:orderId></tns:orderNotFound>");
    }

    @Test
    void twoInlineSchemasImportingByNamespace() throws Exception {
        XsdJsonDataFormat df = dataFormat("classpath:xsdjson/two-schemas.wsdl", d -> d.setElement("lookup"));
        String xml = "<m:lookup xmlns:m=\"urn:messages\"><m:item><code>X</code><count>4294967295</count></m:item></m:lookup>";
        String json = toJson(df, xml);
        assertThat(json).isEqualTo("{\"item\":{\"code\":\"X\",\"count\":4294967295}}");
        assertThat(toXml(df, json)).endsWith(
                "<m:lookup xmlns:m=\"urn:messages\"><m:item><code>X</code><count>4294967295</count></m:item></m:lookup>");
    }
}
