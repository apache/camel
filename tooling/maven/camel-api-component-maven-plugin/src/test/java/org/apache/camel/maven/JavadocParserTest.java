/**
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
import java.net.URL;
import javax.swing.text.html.parser.DTD;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test JavadocParser using {@link java.lang.String} javadoc for Java 6, 7 and 8.
 */
public class JavadocParserTest extends Assert {

    private static final String JAVA6_STRING = "https://docs.oracle.com/javase/6/docs/api/java/lang/String.html";
    private static final String JAVA7_STRING = "https://docs.oracle.com/javase/7/docs/api/java/lang/String.html";
    private static final String JAVA8_STRING = "https://docs.oracle.com/javase/8/docs/api/java/lang/String.html";

    @Test
    public void testGetMethods() throws Exception {
        final DTD dtd = DTD.getDTD("html.dtd");
        final String javaDocPath = String.class.getName().replaceAll("\\.", "/") + ".html";
        final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);

        htmlParser.parse(new InputStreamReader(new URL(JAVA6_STRING).openStream(), "UTF-8"));
        assertNull("Java6 getErrorMessage", htmlParser.getErrorMessage());
        assertEquals("Java6 getMethods", 65, htmlParser.getMethods().size());
        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(new URL(JAVA7_STRING).openStream(), "UTF-8"));
        assertNull("Java7 getErrorMessage", htmlParser.getErrorMessage());
        assertEquals("Java7 getMethods", 65, htmlParser.getMethods().size());
        htmlParser.reset();

        htmlParser.parse(new InputStreamReader(new URL(JAVA8_STRING).openStream(), "UTF-8"));
        assertNull("Java8 getErrorMessage", htmlParser.getErrorMessage());
        assertEquals("Java8 getMethods", 67, htmlParser.getMethods().size());
    }
}
