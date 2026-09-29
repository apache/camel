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
package org.apache.camel.component.properties;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertiesParserEdgeCasesTest {

    private CamelContext context;

    private CamelContext createContext(boolean nested) {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();
        Properties prop = new Properties();
        prop.setProperty("a", "A");
        prop.setProperty("dir", "C:\\temp\\");
        prop.setProperty("file", "f.txt");
        context.getPropertiesComponent().setInitialProperties(prop);
        context.getPropertiesComponent().setNestedPlaceholder(nested);
        context.start();
        return context;
    }

    @AfterEach
    public void stop() {
        if (context != null) {
            context.stop();
        }
        System.clearProperty("camel.parser.test");
    }

    @Test
    public void testOptionalFunction() {
        System.setProperty("camel.parser.test", "S");
        createContext(true);
        assertEquals("S", context.resolvePropertyPlaceholders("{{sys:?camel.parser.test}}"));
        assertEquals("S", context.resolvePropertyPlaceholders("{{?sys:camel.parser.test}}"));
        assertNull(context.resolvePropertyPlaceholders("{{sys:?camel.parser.nope}}"));
        assertNull(context.resolvePropertyPlaceholders("{{?sys:camel.parser.nope}}"));
    }

    @Test
    public void testIgnoreMissingProperty() {
        createContext(true);
        context.getPropertiesComponent().setIgnoreMissingProperty(true);
        assertEquals("{{nope}}", context.resolvePropertyPlaceholders("{{nope}}"));
        assertEquals("x-{{nope}}-A", context.resolvePropertyPlaceholders("x-{{nope}}-{{a}}"));
    }

    @Test
    public void testValueEndingWithBackslash() {
        createContext(true);
        assertEquals("C:\\temp\\f.txt", context.resolvePropertyPlaceholders("{{dir}}{{file}}"));
        createContext(false);
        assertEquals("C:\\temp\\f.txt", context.resolvePropertyPlaceholders("{{dir}}{{file}}"));
    }

    @Test
    public void testNotNestedOptional() {
        createContext(false);
        assertEquals("abcdef", context.resolvePropertyPlaceholders("abc{{?x}}def"));
        assertEquals("abc", context.resolvePropertyPlaceholders("abc{{?x}}"));
        assertEquals("", context.resolvePropertyPlaceholders("{{?x}}{{?y}}"));
    }

    @Test
    public void testEscapedBackslash() {
        createContext(true);
        assertEquals("x\\A", context.resolvePropertyPlaceholders("x\\\\{{a}}"));
        createContext(false);
        assertEquals("x\\A", context.resolvePropertyPlaceholders("x\\\\{{a}}"));
    }

    @Test
    public void testNestedOptionOnlyInPlaceholder() {
        createContext(true);
        assertEquals("http://x?nested=true&A", context.resolvePropertyPlaceholders("http://x?nested=true&{{a}}"));
    }
}
