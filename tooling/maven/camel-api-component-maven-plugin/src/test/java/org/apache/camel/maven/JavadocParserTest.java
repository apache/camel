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
package org.apache.camel.maven;

import java.io.InputStreamReader;
import java.util.Map;

import javax.swing.text.html.parser.DTD;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test JavadocParser using {@link java.lang.String} javadoc for Java 8 and 11.
 */
public class JavadocParserTest {

    @Test
    public void testGetMethods() throws Exception {
        final DTD dtd = DTD.getDTD("html.dtd");
        final String javaDocPath = String.class.getName().replaceAll("\\.", "/") + ".html";
        final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java8_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java8 getErrorMessage");
        assertEquals(67, htmlParser.getMethods().size(), "Java8 getMethods");

        // should include javadoc for parameters
        Map<String, String> params = htmlParser.getParameters().get("offsetByCodePoints");
        assertEquals(2, params.size());
        assertEquals("The index to be offset", params.get("index"));
        assertEquals("The offset in code points", params.get("codePointOffset"));

        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java11_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java11 getErrorMessage");
        assertEquals(75, htmlParser.getMethods().size(), "Java11 getMethods");

        // should include javadoc for parameters
        params = htmlParser.getParameters().get("offsetByCodePoints");
        assertEquals(2, params.size());
        assertEquals("The index to be offset", params.get("index"));
        assertEquals("The offset in code points", params.get("codePointOffset"));

        htmlParser.reset();
    }

    @Test
    public void testGetMethodsBox() throws Exception {
        final DTD dtd = DTD.getDTD("html.dtd");
        final String javaDocPath = String.class.getName().replaceAll("\\.", "/") + ".html";
        final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);

        htmlParser.parse(
                new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/BoxCollaborationsManager.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java8 getErrorMessage");
        assertEquals(7, htmlParser.getMethods().size(), "Java8 getMethods");

        // should include javadoc for parameters
        Map<String, String> params = htmlParser.getParameters().get("addFolderCollaboration");
        assertEquals(3, params.size());
        assertEquals("The role of the collaborator", params.get("role"));
        assertEquals("The folder id", params.get("folderId"));
        assertEquals("The collaborator, which is blah 123 and - something more.", params.get("collaborator"));

        htmlParser.reset();
    }

}
