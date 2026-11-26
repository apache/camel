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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The constructs that phase 1 does not map fail with a message that names them, when a message reaches them.
 */
class XsdJsonUnsupportedTest extends XsdJsonTestSupport {

    private static final String SCHEMA = "classpath:xsdjson/unsupported.xsd";

    @Test
    void mixedContent() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        assertThatThrownBy(() -> toJson(df, "<mixed xmlns=\"urn:unsupported\">Hello <b>World</b></mixed>"))
                .hasMessage("Cannot map /mixed: the type (anonymous type) has mixed content, which is not supported");
    }

    @Test
    void anyContent() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("open"));
        // the type is usable as long as no wildcard content occurs
        assertThat(toJson(df, "<open xmlns=\"urn:unsupported\"><id>1</id></open>")).isEqualTo("{\"id\":\"1\"}");
        assertThatThrownBy(() -> toJson(df, "<open xmlns=\"urn:unsupported\"><id>1</id><x:ext xmlns:x=\"urn:x\"/></open>"))
                .hasMessage("Cannot map /open/ext: the element {urn:x}ext matches xs:any, and xs:any content is not supported");
        assertThatThrownBy(() -> toXml(df, "{\"id\":\"1\",\"ext\":{}}"))
                .hasMessage("Cannot map /open/ext: the field would match xs:any, and xs:any content is not supported");
    }

    @Test
    void anyType() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        assertThatThrownBy(() -> toJson(df, "<untyped xmlns=\"urn:unsupported\">x</untyped>"))
                .hasMessage("Cannot map /untyped: the element {urn:unsupported}untyped has no type, and xs:anyType content is"
                            + " not supported");
    }

    @Test
    void substitutionGroup() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> d.setElement("holder"));
        String xml = "<holder xmlns=\"urn:unsupported\"><circle><size>2</size></circle></holder>";
        assertThatThrownBy(() -> toJson(df, xml))
                .hasMessage(
                        "Cannot map /holder/circle: the element {urn:unsupported}circle is a member of a substitution group,"
                            + " which is not supported");
        assertThatThrownBy(() -> toXml(df, "{\"shape\":{\"size\":2}}"))
                .hasMessage("Cannot map /holder/shape: the element {urn:unsupported}shape is abstract, and substitution groups"
                            + " are not supported");
    }

    @Test
    void abstractTypeAndXsiType() throws Exception {
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
        });
        assertThatThrownBy(() -> toJson(df, "<pet xmlns=\"urn:unsupported\"><name>Rex</name></pet>"))
                .hasMessage("Cannot map /pet: the type {urn:unsupported}Animal is abstract, and xsi:type is not supported");

        XsdJsonDataFormat shape = dataFormat(SCHEMA, d -> {
        });
        String xml = "<circle xmlns=\"urn:unsupported\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                     + " xmlns:u=\"urn:unsupported\" xsi:type=\"u:Square\"><size>2</size></circle>";
        assertThatThrownBy(() -> toJson(shape, xml))
                .hasMessage(
                        "Cannot map /circle: xsi:type=\"u:Square\" is not supported (only the declared type of the element)");
        // the declared type is fine
        assertThat(toJson(shape, xml.replace("u:Square", "u:Shape"))).isEqualTo("{\"size\":2}");
    }

    @Test
    void nameConflicts() {
        // with the element option the conflict fails when the data format starts
        assertThatThrownBy(() -> dataFormat(SCHEMA, d -> d.setElement("clash")))
                .hasMessageContaining("has an attribute and a child element named id, which JSON cannot tell apart:"
                                      + " set the attributePrefix option");
        assertThatThrownBy(() -> dataFormat(SCHEMA, d -> d.setElement("twice")))
                .hasMessageContaining("has two child elements with the local name a");

        // a prefix solves the attribute conflict
        XsdJsonDataFormat df = dataFormat(SCHEMA, d -> {
            d.setElement("clash");
            d.setAttributePrefix("_");
        });
        assertThat(toJsonUnchecked(df, "<clash xmlns=\"urn:unsupported\" id=\"a\"><id>b</id></clash>"))
                .isEqualTo("{\"_id\":\"a\",\"id\":\"b\"}");
    }

    private String toJsonUnchecked(XsdJsonDataFormat df, String xml) {
        try {
            return toJson(df, xml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
