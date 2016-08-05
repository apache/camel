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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests if the default camel context is able to resolve components and data formats using both their real names and/or fallback names.
 * Fallback names have been introduced to avoid name clash in some registries (eg. Spring application context) between components and other camel features.
 */
public class DefaultCamelContextResolverTest {

    private static CamelContext context;

    @BeforeClass
    public static void createContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        context = new DefaultCamelContext(registry);

        // Add a component using its fallback name
        registry.put("green-component", new SampleComponent(true));

        // Add a data format using its fallback name
        registry.put("green-dataformat", new SampleDataFormat(true));

        // Add a language using its fallback name
        registry.put("green-language", new SampleLanguage(true));

        // Add a component using both names
        registry.put("yellow", new SampleComponent(false));
        registry.put("yellow-component", new SampleComponent(true));

        // Add a data format using both names
        registry.put("red", new SampleDataFormat(false));
        registry.put("red-dataformat", new SampleDataFormat(true));

        // Add a language using both names
        registry.put("blue", new SampleLanguage(false));
        registry.put("blue-language", new SampleLanguage(true));

        context.start();
    }

    @AfterClass
    public static void destroyContext() throws Exception {
        context.stop();
        context = null;
    }

    @Test
    public void testComponentWithFallbackNames() throws Exception {
        Component component = context.getComponent("green");
        assertNotNull("Component not found in registry", component);
        assertTrue("Wrong instance type of the Component", component instanceof SampleComponent);
        assertTrue("Here we should have the fallback Component", ((SampleComponent) component).isFallback());
    }

    @Test
    public void testComponentWithBothNames() throws Exception {
        Component component = context.getComponent("yellow");
        assertNotNull("Component not found in registry", component);
        assertTrue("Wrong instance type of the Component", component instanceof SampleComponent);
        assertFalse("Here we should NOT have the fallback Component", ((SampleComponent) component).isFallback());
    }

    @Test
    public void testDataFormatWithFallbackNames() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("green");
        assertNotNull("DataFormat not found in registry", dataFormat);
        assertTrue("Wrong instance type of the DataFormat", dataFormat instanceof SampleDataFormat);
        assertTrue("Here we should have the fallback DataFormat", ((SampleDataFormat) dataFormat).isFallback());
    }

    @Test
    public void testDataformatWithBothNames() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("red");
        assertNotNull("DataFormat not found in registry", dataFormat);
        assertTrue("Wrong instance type of the DataFormat", dataFormat instanceof SampleDataFormat);
        assertFalse("Here we should NOT have the fallback DataFormat", ((SampleDataFormat) dataFormat).isFallback());
    }

    @Test
    public void testLanguageWithFallbackNames() throws Exception {
        Language language = context.resolveLanguage("green");
        assertNotNull("Language not found in registry", language);
        assertTrue("Wrong instance type of the Language", language instanceof SampleLanguage);
        assertTrue("Here we should have the fallback Language", ((SampleLanguage) language).isFallback());
    }

    @Test
    public void testLanguageWithBothNames() throws Exception {
        Language language = context.resolveLanguage("blue");
        assertNotNull("Language not found in registry", language);
        assertTrue("Wrong instance type of the Language", language instanceof SampleLanguage);
        assertFalse("Here we should NOT have the fallback Language", ((SampleLanguage) language).isFallback());
    }

    @Test
    public void testNullLookupComponent() throws Exception {
        Component component = context.getComponent("xxxxxxxxx");
        assertNull("Non-existent Component should be null", component);
    }

    @Test
    public void testNullLookupDataFormat() throws Exception {
        DataFormat dataFormat = context.resolveDataFormat("xxxxxxxxx");
        assertNull("Non-existent DataFormat should be null", dataFormat);
    }

    @Test(expected = NoSuchLanguageException.class)
    public void testNullLookupLanguage() throws Exception {
        context.resolveLanguage("xxxxxxxxx");
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

    public static class SampleDataFormat implements DataFormat {

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
