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
package org.apache.camel.core.osgi;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.Language;
import org.junit.Test;

public class OsgiLanguageResolverTest extends CamelOsgiTestSupport {

    @Test
    public void testOsgiResolverFindLanguageTest() throws IOException {
        CamelContext camelContext = new DefaultCamelContext();
        OsgiLanguageResolver resolver = new OsgiLanguageResolver(getBundleContext());
        Language language = resolver.resolveLanguage("simple", camelContext);
        assertNotNull("We should find simple language", language);
    }

    @Test
    public void testOsgiResolverFindLanguageFallbackTest() throws IOException {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("fuffy-language", new SampleLanguage(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiLanguageResolver resolver = new OsgiLanguageResolver(getBundleContext());
        Language language = resolver.resolveLanguage("fuffy", camelContext);
        assertNotNull("We should find fuffy language", language);
        assertTrue("We should find the fallback language", ((SampleLanguage) language).isFallback());
    }

    @Test
    public void testOsgiResolverFindLanguageDoubleFallbackTest() throws IOException {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("fuffy", new SampleLanguage(false));
        registry.put("fuffy-language", new SampleLanguage(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiLanguageResolver resolver = new OsgiLanguageResolver(getBundleContext());
        Language language = resolver.resolveLanguage("fuffy", camelContext);
        assertNotNull("We should find fuffy language", language);
        assertFalse("We should NOT find the fallback language", ((SampleLanguage) language).isFallback());
    }

    private static class SampleLanguage implements Language {

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
