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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XsdJsonDataFormatTest extends XsdJsonTestSupport {

    private static final String SCHEMA = "classpath:xsdjson/shop.xsd";

    private static final String ORDER_XML = """
            <s:order xmlns:s="urn:shop" xmlns:c="urn:common" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2">
              <s:orderId>A-1</s:orderId>
              <s:status>NEW</s:status>
              <s:created>2026-11-22T10:00:00Z</s:created>
              <s:customer id="42"><s:name>Ann</s:name><s:vip>1</s:vip></s:customer>
              <c:address country="DK"><street>Main 1</street><zip>08000</zip></c:address>
              <s:line lineNo="1">
                <s:sku>AB-1</s:sku><s:quantity>2</s:quantity><s:price currency="EUR">9.950</s:price>
              </s:line>
              <s:card>VISA</s:card>
              <s:tags>red  big</s:tags>
              <s:scores>1 2 3</s:scores>
              <s:total>0.00000001</s:total>
              <s:weight>1.5E3</s:weight>
              <s:comment xsi:nil="true"/>
            </s:order>
            """;

    private static final String ORDER_JSON = "{\"version\":2,\"orderId\":\"A-1\",\"status\":\"NEW\","
                                             + "\"created\":\"2026-11-22T10:00:00Z\",\"customer\":{\"id\":42,\"name\":\"Ann\",\"vip\":true},"
                                             + "\"address\":{\"country\":\"DK\",\"street\":\"Main 1\",\"zip\":\"08000\"},"
                                             + "\"line\":[{\"lineNo\":1,\"sku\":\"AB-1\",\"quantity\":2,"
                                             + "\"price\":{\"currency\":\"EUR\",\"value\":9.950}}],"
                                             + "\"card\":\"VISA\",\"tags\":[\"red\",\"big\"],\"scores\":[1,2,3],"
                                             + "\"total\":0.00000001,\"weight\":1500.0,\"comment\":null}";

    @Test
    void unmarshalTypesArraysAttributesAndNil() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        // numbers, booleans (1), a one-item array, attributes, simple content, xs:list, decimals kept exact, nil
        assertThat(toJson(df, ORDER_XML)).isEqualTo(ORDER_JSON);
    }

    @Test
    void roundTrip() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        String xml = toXml(df, ORDER_JSON);
        assertThat(toJson(df, xml)).isEqualTo(ORDER_JSON);
    }

    @Test
    void marshalWritesSchemaOrderAndNamespaces() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("{urn:shop}order"));
        // the fields in another order than the schema
        String json = "{\"comment\":\"thanks\",\"total\":19.90,\"card\":\"VISA\","
                      + "\"line\":{\"price\":{\"value\":9.95,\"currency\":\"EUR\"},\"quantity\":2,\"sku\":\"AB-1\"},"
                      + "\"address\":{\"zip\":\"8000\",\"street\":\"Main 1\"},"
                      + "\"customer\":{\"vip\":false,\"name\":\"Ann\"},\"created\":\"2026-11-22T10:00:00Z\","
                      + "\"status\":\"NEW\",\"orderId\":\"A-1\",\"version\":2}";
        String xml = toXml(df, json);
        assertThat(xml).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                  + "<s:order xmlns:s=\"urn:shop\" version=\"2\">"
                                  + "<s:orderId>A-1</s:orderId><s:status>NEW</s:status>"
                                  + "<s:created>2026-11-22T10:00:00Z</s:created>"
                                  + "<s:customer><s:name>Ann</s:name><s:vip>false</s:vip></s:customer>"
                                  // urn:common has unqualified children
                                  + "<ns2:address xmlns:ns2=\"urn:common\"><street>Main 1</street><zip>8000</zip></ns2:address>"
                                  // a single object where the schema has an array
                                  + "<s:line><s:sku>AB-1</s:sku><s:quantity>2</s:quantity>"
                                  + "<s:price currency=\"EUR\">9.95</s:price></s:line>"
                                  + "<s:card>VISA</s:card><s:total>19.90</s:total><s:comment>thanks</s:comment>"
                                  + "</s:order>");
    }

    @Test
    void marshalFromMap() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("price"));
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("value", 12.5);
        price.put("currency", "DKK");
        assertThat(toXml(df, price)).endsWith("<s:price xmlns:s=\"urn:shop\" currency=\"DKK\">12.5</s:price>");
    }

    @Test
    void nullWritesNilOrIsLeftOut() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        Map<String, Object> order = order();
        order.put("comment", null);
        // optional, so left out
        order.put("gift", null);
        String xml = toXml(df, order);
        assertThat(xml)
                .contains("<s:comment xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"></s:comment>")
                .doesNotContain("gift");

        order.put("orderId", null);
        assertThatThrownBy(() -> toXml(df, order))
                .hasMessageContaining("/order/orderId is null, but the element is neither nillable nor optional");
    }

    @Test
    void choiceAllowsOneBranch() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        Map<String, Object> order = order();
        order.remove("card");
        order.put("invoice", true);
        assertThat(toXml(df, order)).contains("<s:invoice>true</s:invoice>").doesNotContain("card");

        order.put("card", "VISA");
        assertThatThrownBy(() -> toXml(df, order))
                .hasMessageContaining("has card and invoice, but the schema has an xs:choice that allows only one of them");
    }

    @Test
    void allKeepsTheJsonOrder() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        Map<String, Object> order = order();
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("ribbon", "gold");
        extras.put("wrap", true);
        order.put("extras", extras);
        assertThat(toXml(df, order)).contains("<s:extras><s:ribbon>gold</s:ribbon><s:wrap>true</s:wrap></s:extras>");
    }

    @Test
    void repeatingSequence() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("pairs"));
        String xml = "<pairs xmlns=\"urn:shop\"><key>a</key><val>1</val><key>b</key><val>2</val></pairs>";
        String json = toJson(df, xml);
        assertThat(json).isEqualTo("{\"key\":[\"a\",\"b\"],\"val\":[1,2]}");
        assertThat(toXml(df, json))
                .endsWith(
                        "<s:pairs xmlns:s=\"urn:shop\"><s:key>a</s:key><s:val>1</s:val><s:key>b</s:key><s:val>2</s:val></s:pairs>");
    }

    @Test
    void recursiveType() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("tree"));
        String xml
                = "<tree xmlns=\"urn:shop\" name=\"root\"><node name=\"a\"><node name=\"a1\"/></node><node name=\"b\"/></tree>";
        String json = toJson(df, xml);
        assertThat(json).isEqualTo(
                "{\"name\":\"root\",\"node\":[{\"name\":\"a\",\"node\":[{\"name\":\"a1\"}]},{\"name\":\"b\"}]}");
        assertThat(toJson(df, toXml(df, json))).isEqualTo(json);
    }

    @Test
    void attributePrefixAndTextName() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
            d.setAttributePrefix("@");
            d.setTextName("#text");
        });
        String json = toJson(df, "<price xmlns=\"urn:shop\" currency=\"EUR\">1.10</price>");
        assertThat(json).isEqualTo("{\"@currency\":\"EUR\",\"#text\":1.10}");
    }

    @Test
    void includeRoot() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setIncludeRoot(true));
        String json = toJson(df, "<price xmlns=\"urn:shop\" currency=\"EUR\">1.10</price>");
        assertThat(json).isEqualTo("{\"price\":{\"currency\":\"EUR\",\"value\":1.10}}");
        // the root field names the element, so no element option is needed to marshal
        assertThat(toXml(df, json)).endsWith("<s:price xmlns:s=\"urn:shop\" currency=\"EUR\">1.10</s:price>");
    }

    @Test
    void prettyPrint() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
            d.setPrettyPrint(true);
            d.setElement("pairs");
        });
        String xml = "<pairs xmlns=\"urn:shop\"><key>a</key><val>1</val></pairs>";
        String json = toJson(df, xml);
        assertThat(json).isEqualTo("""
                {
                  "key": [
                    "a"
                  ],
                  "val": [
                    1
                  ]
                }""");
        assertThat(toXml(df, json)).endsWith("""
                <s:pairs xmlns:s="urn:shop">
                  <s:key>a</s:key>
                  <s:val>1</s:val>
                </s:pairs>""");
    }

    @Test
    void headerChoosesTheElement() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        Exchange exchange = exchange();
        exchange.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, "price");
        assertThat(toXml(df, exchange, "{\"currency\":\"EUR\",\"value\":2}")).endsWith(
                "<s:price xmlns:s=\"urn:shop\" currency=\"EUR\">2</s:price>");

        // on unmarshal the header restricts the root element
        Exchange other = exchange();
        other.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, "{urn:shop}tree");
        assertThatThrownBy(() -> toJson(df, other, "<price xmlns=\"urn:shop\" currency=\"EUR\">1</price>"))
                .hasMessageContaining("The XML root element is {urn:shop}price but the element {urn:shop}tree is expected");

        // the header may only name a global element of the schema
        Exchange unknown = exchange();
        unknown.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, "sku");
        assertThatThrownBy(() -> toXml(df, unknown, "{}"))
                .hasMessageContaining("The schema has no global element sku");
    }

    @Test
    void marshalNeedsTheElement() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        assertThatThrownBy(() -> toXml(df, "{\"currency\":\"EUR\",\"value\":2}"))
                .hasMessageContaining("The element option or the CamelXsdJsonElement header must name the root element");
    }

    @Test
    void unknownFields() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("price"));
        assertThatThrownBy(() -> toXml(df, "{\"currency\":\"EUR\",\"value\":2,\"discount\":1}"))
                .hasMessageContaining("The JSON field /price/discount is not declared by the type {urn:shop}Money");
        assertThatThrownBy(() -> toJson(df, "<price xmlns=\"urn:shop\" currency=\"EUR\" discount=\"1\">2</price>"))
                .hasMessageContaining("The attribute discount of /price is not declared by the type {urn:shop}Money");

        XsdJsonDataFormat tree = dataFormat(SCHEMA, d -> d.setElement("tree"));
        assertThatThrownBy(() -> toJson(tree, "<tree xmlns=\"urn:shop\"><leaf/></tree>"))
                .hasMessageContaining("The element {urn:shop}leaf at /tree/leaf is not declared by the type {urn:shop}Node");

        XsdJsonDataFormat lenient = dataFormat(SCHEMA, d -> {
            d.setElement("tree");
            d.setIgnoreUnknown(true);
        });
        assertThat(toJson(lenient, "<tree xmlns=\"urn:shop\" color=\"red\"><leaf><x/></leaf><node/></tree>"))
                .isEqualTo("{\"node\":[{}]}");
        assertThat(toXml(lenient, "{\"node\":[{}],\"color\":\"red\"}"))
                .endsWith("<s:tree xmlns:s=\"urn:shop\"><s:node></s:node></s:tree>");
    }

    @Test
    void wrongNamespace() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        // the children of urn:common are unqualified
        String xml = "<address xmlns=\"urn:common\"><street>Main 1</street><zip>1</zip></address>";
        assertThatThrownBy(() -> toJson(df, xml))
                .hasMessageContaining(
                        "The element /address/street is in the namespace 'urn:common' but the schema declares it in ''");
    }

    @Test
    void invalidValues() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        assertThatThrownBy(() -> toJson(df, "<price xmlns=\"urn:shop\" currency=\"EUR\">cheap</price>"))
                .hasMessageContaining("The value 'cheap' at /price is not a number");
        assertThatThrownBy(() -> toJson(df, "<pairs xmlns=\"urn:shop\"><key>a</key><val>yes</val></pairs>"))
                .hasMessageContaining("The value 'yes' at /pairs/val is not a number");
    }

    @Test
    void arrayForASingleElementFails() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        Map<String, Object> order = order();
        order.put("orderId", List.of("A-1", "A-2"));
        assertThatThrownBy(() -> toXml(df, order))
                .hasMessageContaining(
                        "The JSON field /order/orderId is an array, but the element {urn:shop}orderId occurs at most once");
    }

    @Test
    void notJson() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("order"));
        assertThatThrownBy(() -> toXml(df, "<order/>")).hasMessageContaining("The message body is not JSON");
    }

    private static Map<String, Object> order() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) Jsoner.deserialize(ORDER_JSON);
        return order;
    }
}
