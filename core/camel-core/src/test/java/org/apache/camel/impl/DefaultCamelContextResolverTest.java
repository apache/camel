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
package org.apache.camel.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultDataFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests if the default camel context is able to resolve components and data formats using both their real names and/or
 * fallback names. Fallback names have been introduced to avoid name clash in some registries (eg. Spring application
 * context) between components and other camel features.
 */
public class DefaultCamelContextResolverTest {

    private static CamelContext context;

    @BeforeAll
    public static void createContext() throws Exception {
        context = new DefaultCamelContext();

        // Add a component using its fallback name
        context.getRegistry().bind("green-component", new SampleComponent(true));

        // Add a data format using its fallback name
        context.getRegistry().bind("green-dataformat", new SampleDataFormat(true));

        // Add a language using its fallback name
        context.getRegistry().bind("green-language", new SampleLanguage(true));

        // Add a component using both names
        context.getRegistry().bind("yellow", new SampleComponent(false));
        context.getRegistry().bind("yellow-component", new SampleComponent(true));

        // Add a data format using both names
        context.getRegistry().bind("red", new SampleDataFormat(false));
        context.getRegistry().bind("red-dataformat", new SampleDataFormat(true));

        // Add a language using both names
        context.getRegistry().bind("blue", new SampleLanguage(false));
        context.getRegistry().bind("blue-language", new SampleLanguage(true));

        context.start();
    }

    @AfterAll
    public static void destroyContext() throws Exception {
        context.stop();
        context = null;
    }

    @Test
    public void testComponentWithFallbackNames() throws Exception {
        Component component = context.getComponent("green");
        assertNotNull(component, "Component not found in registry");
        boolean b = component instanceof SampleComponent;
        assertTrue(b, "Wrong instance type of the Component");
        assertTrue(((SampleComponent) component).isFallback(), "Here we should have the fallback Component");
    }

    @Test
    public void testComponentWithBothNames() throws Exception {
        Component component = context.getComponent("yellow");
        assertNotNull(component, "Component not found in registry");
        boolean b = component instanceof SampleComponent;
        assertTrue(b, "Wrong instance type of the Component");
        assertFalse(((SampleComponent) component).isFallback(), "Here we should NOT have the fallback Component");
    }

    @Test
    public void testDataFormatWithFallbackNames() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("green");
        assertNotNull(dataFormat, "DataFormat not found in registry");
        boolean b = dataFormat instanceof SampleDataFormat;
        assertTrue(b, "Wrong instance type of the DataFormat");
        assertTrue(((SampleDataFormat) dataFormat).isFallback(), "Here we should have the fallback DataFormat");
    }

    @Test
    public void testDataformatWithBothNames() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("red");
        assertNotNull(dataFormat, "DataFormat not found in registry");
        boolean b = dataFormat instanceof SampleDataFormat;
        assertTrue(b, "Wrong instance type of the DataFormat");
        assertFalse(((SampleDataFormat) dataFormat).isFallback(), "Here we should NOT have the fallback DataFormat");
    }

    @Test
    public void testLanguageWithFallbackNames() throws Exception {
        Language language = context.resolveLanguage("green");
        assertNotNull(language, "Language not found in registry");
        boolean b = language instanceof SampleLanguage;
        assertTrue(b, "Wrong instance type of the Language");
        assertTrue(((SampleLanguage) language).isFallback(), "Here we should have the fallback Language");
    }

    @Test
    public void testLanguageWithBothNames() throws Exception {
        Language language = context.resolveLanguage("blue");
        assertNotNull(language, "Language not found in registry");
        boolean b = language instanceof SampleLanguage;
        assertTrue(b, "Wrong instance type of the Language");
        assertFalse(((SampleLanguage) language).isFallback(), "Here we should NOT have the fallback Language");
    }

    @Test
    public void testNullLookupComponent() throws Exception {
        Component component = context.getComponent("xxxxxxxxx");
        assertNull(component, "Non-existent Component should be null");
    }

    @Test
    public void testNullLookupDataFormat() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("xxxxxxxxx");
        assertNull(dataFormat, "Non-existent DataFormat should be null");
    }

    @Test
    public void testNullLookupLanguage() throws Exception {
        assertThrows(NoSuchLanguageException.class, () -> context.resolveLanguage("xxxxxxxxx"));
    }

    public static class SampleComponent extends DefaultComponent {

        private boolean fallback;

        SampleComponent(boolean fallback) {
            this.fallback = fallback;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            throw new UnsupportedOperationException("Should not be called");
        }

        public boolean isFallback() {
            return fallback;
        }

        public void setFallback(boolean fallback) {
            this.fallback = fallback;
        }
    }

    public static class SampleDataFormat extends DefaultDataFormat {

        private boolean fallback;

        SampleDataFormat(boolean fallback) {
            this.fallback = fallback;
        }

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            throw new UnsupportedOperationException("Should not be called");
        }

        public boolean isFallback() {
            return fallback;
        }

        public void setFallback(boolean fallback) {
            this.fallback = fallback;
        }
    }

    public static class SampleLanguage implements Language {

        private boolean fallback;

        SampleLanguage(boolean fallback) {
            this.fallback = fallback;
        }

        @Override
        public Predicate createPredicate(String expression) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Expression createExpression(String expression) {
            throw new UnsupportedOperationException("Should not be called");
        }

        public boolean isFallback() {
            return fallback;
        }

        public void setFallback(boolean fallback) {
            this.fallback = fallback;
        }
    }

}
