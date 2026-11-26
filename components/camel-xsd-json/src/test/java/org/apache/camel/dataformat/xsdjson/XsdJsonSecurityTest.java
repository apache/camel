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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XsdJsonSecurityTest extends XsdJsonTestSupport {

    @TempDir
    Path dir;

    @Test
    void messageWithDoctypeIsRefused() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "top secret");
        XsdJsonDataFormat df = dataFormat("classpath:xsdjson/shop.xsd", d -> {
        });
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE price [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                     + "<price xmlns=\"urn:shop\" currency=\"&xxe;\">1</price>";
        assertThatThrownBy(() -> toJson(df, xxe)).satisfies(e -> assertThat(e.getMessage()).doesNotContain("top secret"));

        String doctype = "<?xml version=\"1.0\"?><!DOCTYPE price><price xmlns=\"urn:shop\" currency=\"EUR\">1</price>";
        assertThatThrownBy(() -> toJson(df, doctype)).hasMessage("The XML has a DOCTYPE, which is not allowed");
    }

    @Test
    void schemaWithDoctypeIsRefused() {
        assertThatThrownBy(() -> dataFormat("classpath:xsdjson/doctype.xsd", d -> {
        })).hasMessageContaining("Cannot parse the schema classpath:xsdjson/doctype.xsd: a DOCTYPE is not allowed in a schema");
    }

    @Test
    void importWithAnotherSchemeIsRefused() {
        assertThatThrownBy(() -> dataFormat("classpath:xsdjson/import-file.xsd", d -> {
        })).hasMessageContaining("imports or includes file:/etc/common.xsd (file:/etc/common.xsd), which is not loaded:"
                                 + " only classpath: resources, the scheme of the schema option, are followed");
    }

    @Test
    void importsResolveRelativeToTheSchema() throws Exception {
        Path common = dir.resolve("common");
        Files.createDirectories(common);
        Files.writeString(common.resolve("common.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
                  <xs:element name="zip" type="xs:int"/>
                </xs:schema>""");
        Files.writeString(dir.resolve("main.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:main">
                  <xs:import namespace="urn:common" schemaLocation="common/../common/common.xsd"/>
                  <xs:element name="main" type="xs:string"/>
                </xs:schema>""");
        XsdJsonDataFormat df = dataFormat("file:" + dir.resolve("main.xsd"), d -> {
        });
        assertThat(toJson(df, "<zip xmlns=\"urn:common\">8000</zip>")).isEqualTo("8000");
    }

    @Test
    void nestingDepthIsLimited() throws Exception {
        XsdJsonDataFormat df = dataFormat("classpath:xsdjson/shop.xsd", d -> d.setElement("tree"));
        // the XML parser of the JDK may have a lower limit (jdk.xml.maxElementDepth) than the data format,
        // XsdJsonWoodstoxTest checks the limit of the data format in the XML to JSON direction
        assertThat(thrownOnThread(BIG_STACK, () -> toJson(df, nestedXml(600))))
                .satisfies(e -> assertThat(e.getMessage()).containsAnyOf("depth", "nested deeper than 512"));

        // on a stack that holds 512 levels, the limit of the data format applies
        assertThat(thrownOnThread(BIG_STACK, () -> toXml(df, nestedTree(512)))).isNull();
        assertThat(thrownOnThread(BIG_STACK, () -> toXml(df, nestedTree(513))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The JSON is nested deeper than 512 elements at /tree/node[0]/node[0]");
    }

    @Test
    void nestingOnASmallStackIsRefused() throws Exception {
        XsdJsonDataFormat df = dataFormat("classpath:xsdjson/shop.xsd", d -> d.setElement("tree"));
        // load the classes first, so that the stack of the small thread is not exhausted by the class loading
        toXml(df, nestedTree(3));

        // an IllegalArgumentException that says where the stack overflowed, never a StackOverflowError
        assertThat(thrownOnThread(SMALL_STACK, () -> toXml(df, nestedTree(512))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(StackOverflowError.class)
                .hasMessageMatching(
                        "The JSON is nested too deep for the thread stack, which overflowed after the conversion"
                                    + " reached level \\d+"
                                    + " \\(the data format allows 512 levels\\)");
    }

    @Test
    void resolveLocation() {
        assertThat(XsdSchemaLoader.resolveLocation("classpath:a/b/main.wsdl#types1", "../c/common.xsd"))
                .isEqualTo("classpath:a/c/common.xsd");
        assertThat(XsdSchemaLoader.resolveLocation("file:/opt/schemas/main.xsd", "common.xsd"))
                .isEqualTo("file:/opt/schemas/common.xsd");
        assertThat(XsdSchemaLoader.resolveLocation("https://example.com/ws/main.wsdl", "xsd/common.xsd"))
                .isEqualTo("https://example.com/ws/xsd/common.xsd");
        assertThat(XsdSchemaLoader.resolveLocation("https://example.com/ws/main.wsdl", "file:/etc/passwd"))
                .isEqualTo("file:/etc/passwd");
    }
}
