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

import javax.xml.stream.XMLOutputFactory;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs with Woodstox as the StAX writer, which CXF brings (such as in the soap Kamelets). Woodstox fails to close a
 * document that has no root, and that failure must not hide the error that stopped the document. The surefire execution
 * "woodstox" of the pom runs this class with woodstox-core on the classpath; the other tests use the writer of the JDK.
 */
class XsdJsonWoodstoxTest extends XsdJsonTestSupport {

    private static final String WSDL = "classpath:xsdjson/orders.wsdl";

    @Test
    void theWriterIsWoodstox() {
        assertThat(XMLOutputFactory.newFactory().getClass().getName()).startsWith("com.ctc.wstx.");
    }

    @Test
    void anUnknownFieldOfTheRootIsTheErrorThrown() {
        // as the soap-action Kamelet does: the WSDL as the schema, and the element in the header
        XsdJsonDataFormat df = dataFormat(WSDL, d -> {
        });
        Exchange exchange = exchange();
        exchange.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, "{http://example.com/orders}getOrder");

        assertThatThrownBy(() -> toXml(df, exchange, "{\"orderId\":\"42\",\"color\":\"red\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The JSON field /getOrder/color is not declared")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .anySatisfy(s -> assertThat(s).hasMessageContaining("END_DOCUMENT")));
    }

    @Test
    void anUnknownNestedFieldIsTheErrorThrown() {
        XsdJsonDataFormat df = dataFormat(WSDL, d -> d.setElement("placeOrder"));

        assertThatThrownBy(() -> toXml(df, "{\"customer\":\"Ann\",\"item\":[{\"sku\":\"A\",\"color\":\"red\"}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The JSON field /placeOrder/item[0]/color is not declared");
    }

    @Test
    void aCompleteDocumentIsWritten() throws Exception {
        XsdJsonDataFormat df = dataFormat(WSDL, d -> d.setElement("getOrder"));

        assertThat(toXml(df, "{\"orderId\":\"42\"}")).endsWith(
                "<tns:getOrder xmlns:tns=\"http://example.com/orders\"><tns:orderId>42</tns:orderId></tns:getOrder>");
    }
}
