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

import javax.swing.text.html.parser.DTD;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test JavadocParser using {@link java.lang.String} javadoc for Java 6, 7 and 8.
 */
public class JavadocParserTest {

    @Test
    public void testGetMethods() throws Exception {
        final DTD dtd = DTD.getDTD("html.dtd");
        final String javaDocPath = String.class.getName().replaceAll("\\.", "/") + ".html";
        final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java6_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java6 getErrorMessage");
        assertEquals(65, htmlParser.getMethods().size(), "Java6 getMethods");
        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java7_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java7 getErrorMessage");
        assertEquals(65, htmlParser.getMethods().size(), "Java7 getMethods");
        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java8_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java8 getErrorMessage");
        assertEquals(67, htmlParser.getMethods().size(), "Java8 getMethods");
        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(JavadocParserTest.class.getResourceAsStream("/Java11_String.html"), "UTF-8"));
        assertNull(htmlParser.getErrorMessage(), "Java11 getErrorMessage");
        assertEquals(75, htmlParser.getMethods().size(), "Java11 getMethods");
        htmlParser.reset();
    }
}
