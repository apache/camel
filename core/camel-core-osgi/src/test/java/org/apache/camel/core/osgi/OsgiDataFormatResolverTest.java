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
package org.apache.camel.core.osgi;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultDataFormat;
import org.apache.camel.support.DefaultRegistry;
import org.junit.Test;

public class OsgiDataFormatResolverTest extends CamelOsgiTestSupport {

    @Test
    public void testOsgiResolverFindDataFormatFallbackTest() throws Exception {
        Registry registry = new DefaultRegistry();
        registry.bind("allstar-dataformat", new SampleDataFormat(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiDataFormatResolver resolver = new OsgiDataFormatResolver(getBundleContext());
        DataFormat dataformat = resolver.resolveDataFormat("allstar", camelContext);
        assertNotNull("We should find the super dataformat", dataformat);
        assertTrue("We should get the super dataformat here", dataformat instanceof SampleDataFormat);
    }

    @Test
    public void testOsgiResolverFindLanguageDoubleFallbackTest() throws Exception {
        Registry registry = new DefaultRegistry();
        registry.bind("allstar", new SampleDataFormat(false));
        registry.bind("allstar-dataformat", new SampleDataFormat(true));

        CamelContext camelContext = new DefaultCamelContext(registry);

        OsgiDataFormatResolver resolver = new OsgiDataFormatResolver(getBundleContext());
        DataFormat dataformat = resolver.resolveDataFormat("allstar", camelContext);
        assertNotNull("We should find the super dataformat", dataformat);
        assertTrue("We should get the super dataformat here", dataformat instanceof SampleDataFormat);
        assertFalse("We should NOT find the fallback dataformat", ((SampleDataFormat) dataformat).isFallback());
    }

    private static class SampleDataFormat extends DefaultDataFormat {

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

}
