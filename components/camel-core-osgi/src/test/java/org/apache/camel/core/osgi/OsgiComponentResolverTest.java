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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.Test;

public class OsgiComponentResolverTest extends CamelOsgiTestSupport {

    @Test
    public void testOsgiResolverFindComponentTest() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        OsgiComponentResolver resolver = new OsgiComponentResolver(getBundleContext());
        Component component = resolver.resolveComponent("file_test", camelContext);
        assertNotNull("We should find file_test component", component);
        assertTrue("We should get the file component here", component instanceof FileComponent);
    }

    @Test
    public void testOsgiResolverFindComponentFallbackTest() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("allstar-component", new SampleComponent(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiComponentResolver resolver = new OsgiComponentResolver(getBundleContext());
        Component component = resolver.resolveComponent("allstar", camelContext);
        assertNotNull("We should find the super component", component);
        assertTrue("We should get the super component here", component instanceof SampleComponent);
    }

    @Test
    public void testOsgiResolverFindLanguageDoubleFallbackTest() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("allstar", new SampleComponent(false));
        registry.put("allstar-component", new SampleComponent(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiComponentResolver resolver = new OsgiComponentResolver(getBundleContext());
        Component component = resolver.resolveComponent("allstar", camelContext);
        assertNotNull("We should find the super component", component);
        assertTrue("We should get the super component here", component instanceof SampleComponent);
        assertFalse("We should NOT find the fallback component", ((SampleComponent) component).isFallback());
    }

    private static class SampleComponent implements Component {

        private boolean fallback;

        SampleComponent(boolean fallback) {
            this.fallback = fallback;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public CamelContext getCamelContext() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Endpoint createEndpoint(String uri) throws Exception {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean useRawUri() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public EndpointConfiguration createConfiguration(String uri) throws Exception {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ComponentConfiguration createComponentConfiguration() {
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
