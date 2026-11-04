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
package org.apache.camel.component.flowable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.w3c.dom.Document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CamelStringToXmlDocumentDeserializerTest {

    private final CamelStringToXmlDocumentDeserializer deserializer = new CamelStringToXmlDocumentDeserializer();

    @Test
    public void xmlEventIsDeserialized() {
        Document document = deserializer.deserialize("<user><name>John Doe</name><age>23</age></user>");

        assertEquals("user", document.getDocumentElement().getNodeName());
        assertEquals("John Doe", document.getElementsByTagName("name").item(0).getTextContent());
    }

    @Test
    public void entityDeclaredInDoctypeIsNotExpandedFromFile() throws Exception {
        Path file = Path.of("target", "flowable-xml-deserializer", "entity.txt").toAbsolutePath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content-of-the-entity-file", StandardCharsets.UTF_8);

        Document document = deserializer.deserialize(
                "<?xml version=\"1.0\"?><!DOCTYPE user [<!ENTITY content SYSTEM \"" + file.toUri() + "\">]>"
                                                     + "<user><name>&content;</name></user>");

        assertFalse(document.getDocumentElement().getTextContent().contains("content-of-the-entity-file"));
    }

    @Test
    public void externalDtdIsNotLoaded() {
        Path missing = Path.of("target", "flowable-xml-deserializer", "missing.dtd").toAbsolutePath();

        Document document = deserializer.deserialize(
                "<?xml version=\"1.0\"?><!DOCTYPE user SYSTEM \"" + missing.toUri() + "\"><user><name>John Doe</name></user>");

        assertEquals("John Doe", document.getElementsByTagName("name").item(0).getTextContent());
    }
}
