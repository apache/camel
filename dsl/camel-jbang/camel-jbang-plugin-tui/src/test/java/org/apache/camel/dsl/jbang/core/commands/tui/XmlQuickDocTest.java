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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quick doc of the XML element or attribute the cursor is on (CAMEL-25244).
 */
class XmlQuickDocTest {

    private static CamelCatalog catalog;

    private static final List<String> ROUTE = List.of(
            "<routes xmlns=\"http://camel.apache.org/schema/xml-io\">",
            "    <route>",
            "        <from uri=\"kafka:orders\"/>",
            "        <log message=\"Got ${body}\" loggingLevel=\"WARN\"/>",
            "        <split parallelProcessing=\"true\">",
            "            <simple>${body}</simple>",
            "        </split>",
            "    </route>",
            "</routes>");

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    private static List<String> doc(int row, String on) {
        int col = ROUTE.get(row).indexOf(on) + 2;
        return XmlQuickDoc.at(catalog, ROUTE, row, col).stream().map(SourceViewer.DocEntry::text).toList();
    }

    @Test
    void anAttributeByItsNameOrValue() {
        List<String> doc = doc(3, "loggingLevel");
        assertThat(doc.get(0)).startsWith("log loggingLevel — Sets the logging level");
        // its documentation lists the levels and the default already: not said twice
        assertThat(doc).hasSize(1);
        // in the value, the same attribute
        assertThat(doc(3, "WARN").get(0)).startsWith("log loggingLevel");
        // a boolean option
        assertThat(doc(4, "parallelProcessing")).anyMatch(t -> t.contains("Values: true, false"));
    }

    @Test
    void anElementByItsName() {
        List<String> doc = doc(3, "log ");
        assertThat(doc.get(0)).startsWith("log — ");
        assertThat(doc).contains("Required: message");
    }

    @Test
    void theUriIsLeftToTheEndpointDocOfTheLine() {
        assertThat(doc(2, "kafka")).isEmpty();
        // nor anything in the text of an element, or in the view without a cursor
        assertThat(XmlQuickDoc.at(catalog, ROUTE, 6, 2)).isEmpty();
        assertThat(XmlQuickDoc.at(catalog, ROUTE, 3, -1)).isEmpty();
    }
}
