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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XsdJsonSamplesTest extends XsdJsonTestSupport {

    private static final String SAMPLES = "classpath:xsdjson/samples.xsd";

    @Test
    void everyBuiltInTypeHasAValueOfItsType() throws Exception {
        XsdJsonSamples samples = new XsdJsonSamples(context, SAMPLES);

        JsonObject json = (JsonObject) Jsoner.deserialize(samples.json("types"));
        assertThat(json.get("version")).hasToString("1");
        assertThat(json.get("text")).isEqualTo("string");
        assertThat(json.get("flag")).isEqualTo(true);
        assertThat(json.get("count")).hasToString("1");
        assertThat(json.get("negative")).hasToString("-1");
        assertThat(json.get("nonPositive")).hasToString("0");
        assertThat(json.get("amount")).hasToString("9.99");
        assertThat(json.get("dateTime")).isEqualTo("2026-01-31T12:00:00Z");
        assertThat(json.get("date")).isEqualTo("2026-01-31");
        assertThat(json.get("uri")).isEqualTo("http://example.com");
        // the first value of an enumeration, of the type of the field, and of the most derived type
        assertThat(json.get("level")).hasToString("3");
        assertThat(json.get("color")).isEqualTo("red");
        assertThat(json.get("dark")).isEqualTo("blue");
        // lists and repeating elements are arrays with one item
        assertThat(list(json, "tokens")).containsExactly("id1");
        assertThat(list(json, "levels")).hasSize(1);
        assertThat(list(json, "item")).containsExactly("string");
        // the first member of a choice, and the optional elements too
        assertThat(json).containsKeys("email", "optional").doesNotContainKey("phone");
        // a type that contains itself is filled in once
        assertThat(map(json, "root")).containsOnlyKeys("name");

        // the XML is valid
        assertValid("src/test/resources/xsdjson/samples.xsd", samples.xml("types"));
    }

    @Test
    void theXmlIsWhatTheDataFormatWritesAndReadsBackAsTheJson() throws Exception {
        XsdJsonSamples samples = new XsdJsonSamples(context, SAMPLES);
        String xml = samples.xml("{urn:samples}types");

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<t:types xmlns:t=\"urn:samples\" version=\"1\">", "<t:level>3</t:level>",
                        "<t:tokens>id1</t:tokens>");
        XsdJsonDataFormat df = dataFormat(SAMPLES, d -> {
        });
        assertThat(Jsoner.deserialize(toJson(df, xml))).isEqualTo(Jsoner.deserialize(samples.json("types")));
    }

    @ParameterizedTest
    @CsvSource({ "getOrder", "getOrderResponse", "placeOrder", "placeOrderResponse", "orderNotFound" })
    void theMessagesOfAWsdl(String element) throws Exception {
        XsdJsonSamples samples = new XsdJsonSamples(context, "classpath:xsdjson/orders.wsdl");

        String xml = samples.xml(element);
        assertThat(xml).contains("<tns:" + element + " xmlns:tns=\"http://example.com/orders\">");
        XsdJsonDataFormat df = dataFormat("classpath:xsdjson/orders.wsdl", d -> {
        });
        assertThat(Jsoner.deserialize(toJson(df, xml))).isEqualTo(Jsoner.deserialize(samples.json(element)));
    }

    @Test
    void theSampleOfTheShopOrder() throws Exception {
        XsdJsonSamples samples = new XsdJsonSamples(context, "classpath:xsdjson/shop.xsd");

        JsonObject json = (JsonObject) Jsoner.deserialize(samples.json("order"));
        assertThat(json.get("status")).isEqualTo("NEW");
        assertThat(map(json, "customer")).containsKeys("id", "name", "vip");
        // simple content with an attribute
        assertThat(map((JsonObject) list(json, "line").get(0), "price"))
                .containsOnlyKeys("currency", "value");
        assertThat(json).containsKeys("card", "tags", "extras").doesNotContainKey("invoice");
        assertThat(map(json, "address")).containsOnlyKeys("country", "street", "zip");
    }

    @Test
    void theDocumentsAreReadByTheReader() throws Exception {
        List<String> read = new ArrayList<>();
        XsdJsonSamples.DocumentReader reader = uri -> {
            read.add(uri);
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, uri)) {
                return is.readAllBytes();
            }
        };
        XsdJsonSamples samples = new XsdJsonSamples(context, "classpath:xsdjson/shop.xsd", reader);

        assertThat(samples.json("order")).contains("\"street\"");
        assertThat(read).containsExactly("classpath:xsdjson/shop.xsd", "classpath:xsdjson/common/common.xsd");

        // a reader that refuses the import
        XsdJsonSamples.DocumentReader refusing = uri -> {
            if (uri.contains("common")) {
                throw new IOException("not this one");
            }
            return reader.read(uri);
        };
        assertThatThrownBy(() -> new XsdJsonSamples(context, "classpath:xsdjson/shop.xsd", refusing))
                .hasStackTraceContaining("not this one");
    }

    @Test
    void anUnknownElement() {
        XsdJsonSamples samples = new XsdJsonSamples(context, SAMPLES);

        assertThatThrownBy(() -> samples.json("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The schema has no global element nope");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(JsonObject json, String key) {
        return (List<Object>) json.get(key);
    }

    private static Map<String, Object> map(JsonObject json, String key) {
        return (JsonObject) json.get(key);
    }

    private static void assertValid(String xsd, String xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Validator validator = factory.newSchema(Paths.get(xsd).toFile()).newValidator();
        validator.validate(new StreamSource(new StringReader(xml)));
    }
}
