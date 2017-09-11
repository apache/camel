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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EndpointConfigurationTest {

    private static final String MAPPED_SCHEME = "mapped";    
    private static CamelContext context;
    
    @BeforeClass
    public static void createContext() throws Exception {
        context = new DefaultCamelContext();
        Component component = new ConfiguredComponent();
        context.addComponent(MAPPED_SCHEME, component);
        context.start(); // so that TypeConverters are available
    }

    @AfterClass
    public static void destroyContext() throws Exception {
        context.stop();
        context = null;
    }

    @Test
    public void testConfigurationInstanceType() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("mapped:foo", context);
        assertEquals("EndpointConfiguration instance not of expected type", MappedEndpointConfiguration.class, cfg.getClass());
    }

    @Test
    public void testConfigurationEquals() throws Exception {
        EndpointConfiguration cfg1 = ConfigurationHelper.createConfiguration("mapped://foo?one=true&two=2", context);
        EndpointConfiguration cfg2 = ConfigurationHelper.createConfiguration("mapped://foo?two=2&one=true", context);
        String uri1 = cfg1.toUriString(EndpointConfiguration.UriFormat.Complete);
        String uri2 = cfg2.toUriString(EndpointConfiguration.UriFormat.Complete);
        assertEquals("Query parameter order should not matter", uri1, uri2);
    }

    @Test
    @Ignore("Fails due CAMEL-5183")
    public void testConfigurationPortParameter() throws Exception {
        EndpointConfiguration cfg1 = ConfigurationHelper.createConfiguration("mapped://foo:8080?one=true&two=2&port=123", context);
        String uri1 = cfg1.toUriString(EndpointConfiguration.UriFormat.Complete);
        assertEquals("mapped://foo:8080?one=true&port=123&two=2", uri1);
    }

    private static class ConfiguredComponent implements Component {
        private CamelContext context;

        @Override
        public void setCamelContext(CamelContext camelContext) {
            context = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return context;
        }

        @Override
        public Endpoint createEndpoint(String uri) throws Exception {
            return null;
        }

        @Override
        public ComponentConfiguration createComponentConfiguration() {
            return null;
        }

        @Override
        public EndpointConfiguration createConfiguration(String uri) throws Exception {
            return new MappedEndpointConfiguration(getCamelContext());
        }

        @Override
        public boolean useRawUri() {
            return false;
        }
    }
}
