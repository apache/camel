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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.URIField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @see ComponentConfigurationTest for tests using the {@link ComponentConfiguration} mechanism
 */
public class ConfigurationHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationHelperTest.class);
    private static final String URIDUMP_SCHEME = "uri-dump";
    private static final String DUMMY_SCHEME = "dummy";
    
    private CamelContext context;
    
    @Before
    public void createContext() throws Exception {
        context = new DefaultCamelContext();
        Component component = new ConfiguredComponent();
        context.addComponent(URIDUMP_SCHEME, component);
        context.addComponent(DUMMY_SCHEME, component);
        context.start(); // so that TypeConverters are available
    }

    @After
    public void destroyContext() throws Exception {
        context.stop();
        context = null;
    }

    @Test
    public void testUrnNoQuery() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("uri-dump:foo", context);
        logConfigurationObject(cfg);
        assertEquals(URIDUMP_SCHEME, cfg.getParameter(EndpointConfiguration.URI_SCHEME));
        assertEquals("foo", cfg.getParameter(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART));
        assertEquals("URNs don't set the authority field", null, cfg.getParameter(EndpointConfiguration.URI_AUTHORITY));
        assertEquals("URNs don't set the userInfo field", null, cfg.getParameter(EndpointConfiguration.URI_USER_INFO));
        assertEquals("URNs don't set the host field", null, cfg.getParameter(EndpointConfiguration.URI_HOST));
        assertEquals("URNs don't set the port field", Integer.valueOf(-1), cfg.getParameter(EndpointConfiguration.URI_PORT));
        assertEquals("URNs don't set the path field", null, cfg.getParameter(EndpointConfiguration.URI_PATH));
        assertEquals("URNs don't set the query field", null, cfg.getParameter(EndpointConfiguration.URI_QUERY));
        assertEquals("URNs don't set the fragment field", null, cfg.getParameter(EndpointConfiguration.URI_FRAGMENT));
    }

    @Test
    public void testUrnWithQuery() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("uri-dump:hadrian@localhost:9001/context/path/?bar=true&baz=2#1234", context);
        logConfigurationObject(cfg);
        assertEquals(URIDUMP_SCHEME, cfg.getParameter(EndpointConfiguration.URI_SCHEME));
        assertEquals("hadrian@localhost:9001/context/path/?bar=true&baz=2#1234", cfg.getParameter(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART));
        assertEquals("URNs don't set the authority field", null, cfg.getParameter(EndpointConfiguration.URI_AUTHORITY));
        assertEquals("URNs don't set the userInfo field", null, cfg.getParameter(EndpointConfiguration.URI_USER_INFO));
        assertEquals("URNs don't set the host field", null, cfg.getParameter(EndpointConfiguration.URI_HOST));
        assertEquals("URNs don't set the port field", Integer.valueOf(-1), cfg.getParameter(EndpointConfiguration.URI_PORT));
        assertEquals("URNs don't set the path field", null, cfg.getParameter(EndpointConfiguration.URI_PATH));
        assertEquals("URNs don't set the query field", null, cfg.getParameter(EndpointConfiguration.URI_QUERY));
        assertEquals("URNs don't set the fragment field", null, cfg.getParameter(EndpointConfiguration.URI_FRAGMENT));
    }

    @Test
    public void testUrlSimple() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("uri-dump://foo", context);
        logConfigurationObject(cfg);
        assertEquals(URIDUMP_SCHEME, cfg.getParameter(EndpointConfiguration.URI_SCHEME));
        assertEquals("//foo", cfg.getParameter(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART));
        assertEquals("foo", cfg.getParameter(EndpointConfiguration.URI_AUTHORITY));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_USER_INFO));
        assertEquals("foo", cfg.getParameter(EndpointConfiguration.URI_HOST));
        assertEquals(Integer.valueOf(-1), cfg.getParameter(EndpointConfiguration.URI_PORT));
        assertEquals("", cfg.getParameter(EndpointConfiguration.URI_PATH));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_QUERY));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_FRAGMENT));
    }

    @Test
    public void testUrlWithPath() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("uri-dump://foo/bar#defrag", context);
        logConfigurationObject(cfg);
        assertEquals(URIDUMP_SCHEME, cfg.getParameter(EndpointConfiguration.URI_SCHEME));
        assertEquals("//foo/bar#defrag", cfg.getParameter(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART));
        assertEquals("foo", cfg.getParameter(EndpointConfiguration.URI_AUTHORITY));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_USER_INFO));
        assertEquals("foo", cfg.getParameter(EndpointConfiguration.URI_HOST));
        assertEquals(Integer.valueOf(-1), cfg.getParameter(EndpointConfiguration.URI_PORT));
        assertEquals("/bar#defrag", cfg.getParameter(EndpointConfiguration.URI_PATH));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_QUERY));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_FRAGMENT));
    }

    @Test
    public void testUrlWithQuery() throws Exception {
        EndpointConfiguration cfg = ConfigurationHelper.createConfiguration("uri-dump://hadrian@localhost:9001/context/path/?bar=true&baz=2#none", context);
        logConfigurationObject(cfg);
        assertEquals(URIDUMP_SCHEME, cfg.getParameter(EndpointConfiguration.URI_SCHEME));
        assertEquals("//hadrian@localhost:9001/context/path/?bar=true&baz=2#none", cfg.getParameter(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART));
        assertEquals("hadrian@localhost:9001", cfg.getParameter(EndpointConfiguration.URI_AUTHORITY));
        assertEquals("hadrian", cfg.getParameter(EndpointConfiguration.URI_USER_INFO));
        assertEquals("localhost", cfg.getParameter(EndpointConfiguration.URI_HOST));
        assertEquals(Integer.valueOf(9001), cfg.getParameter(EndpointConfiguration.URI_PORT));
        assertEquals("/context/path/", cfg.getParameter(EndpointConfiguration.URI_PATH));
        assertEquals("bar=true&baz=2#none", cfg.getParameter(EndpointConfiguration.URI_QUERY));
        assertEquals(null, cfg.getParameter(EndpointConfiguration.URI_FRAGMENT));
    }

    @Test
    public void testConfigurationFormat() throws Exception {
        EndpointConfiguration config = ConfigurationHelper.createConfiguration("uri-dump:foo", context);
        assertEquals(null, config.toUriString(EndpointConfiguration.UriFormat.Canonical));
        assertEquals(null, config.toUriString(EndpointConfiguration.UriFormat.Provider));
        assertEquals(null, config.toUriString(EndpointConfiguration.UriFormat.Consumer));
        assertEquals(null, config.toUriString(EndpointConfiguration.UriFormat.Complete));
    }

    @Test
    public void testDummyConfiguration() throws Exception {
        
        String configUri = "dummy://foobar?first=one&second=2";
        
        EndpointConfiguration config = ConfigurationHelper.createConfiguration(configUri, context);
        assertNotNull(config);
        assertTrue(config instanceof DummyConfiguration);
        assertEquals("one", config.getParameter("first"));
        assertEquals(Integer.valueOf(2), config.getParameter("second"));
    }
    
    protected static void logConfigurationObject(EndpointConfiguration config) {
        if (config == null) {
            return;
        }
        LOG.info("{} [", config.getClass().getCanonicalName()); 
        LOG.info("  uri={}", config.getURI().toASCIIString()); 
        LOG.info("  fields:"); 

        Class<?> clazz = config.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        // Put the Fields in a Map first for a prettier print
        Map<String, Field> map = new HashMap<String, Field>();

        URIField anno = null;
        for (final Field field : fields) {
            anno = field.getAnnotation(URIField.class);
            String key = anno == null ? field.getName() 
                : (EndpointConfiguration.URI_QUERY.equals(anno.parameter()) ? anno.parameter() : anno.component());
            map.put(key, field);
        }

        // Log standard URI components and remove them from the map
        logConfigurationField(config, map, EndpointConfiguration.URI_SCHEME, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_SCHEME_SPECIFIC_PART, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_AUTHORITY, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_USER_INFO, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_HOST, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_PORT, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_PATH, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_QUERY, true);
        logConfigurationField(config, map, EndpointConfiguration.URI_FRAGMENT, true);

        // Log all other fields
        for (Field f : map.values()) {
            logConfigurationField(config, f);
        }
        LOG.info("]"); 
    }

    protected static void logConfigurationField(EndpointConfiguration config, Map<String, Field> fields, String key, boolean remove) {
        logConfigurationField(config, fields.get(key));
        if (remove) {
            fields.remove(key);
        }
    }

    protected static void logConfigurationField(EndpointConfiguration config, Field field) {
        if (field == null) {
            return;
        }
        URIField anno = field.getAnnotation(URIField.class);
        if (anno != null) {
            LOG.info("  @URIField(component = \"{}\", parameter = \"{}\")", anno.component(), anno.parameter());
        }
        LOG.info("  {} {}={}", new Object[] {field.getType().getName(), field.getName(), config.getParameter(field.getName())});
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
            if (uri.equals(URIDUMP_SCHEME)) {
                return new UriDumpConfiguration(getCamelContext());
            } else if (uri.equals(DUMMY_SCHEME)) {
                return new DummyConfiguration(getCamelContext());
            }
            return null;
        }

        @Override
        public boolean useRawUri() {
            return false;
        }
    }

    public static class UriDumpConfiguration extends DefaultEndpointConfiguration {
        private String scheme;
        private String schemeSpecificPart;
        private String authority;
        private String userInfo;
        private String host;
        private int port;
        private String path;
        private String query;
        private String fragment;

        public UriDumpConfiguration(CamelContext camelContext) {
            super(camelContext);
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getScheme() {
            return scheme;
        }

        public void setSchemeSpecificPart(String schemeSpecificPart) {
            this.schemeSpecificPart = schemeSpecificPart;
        }

        public String getSchemeSpecificPart() {
            return schemeSpecificPart;
        }

        public void setAuthority(String authority) {
            this.authority = authority;
        }

        public String getAuthority() {
            return authority;
        }

        public void setUserInfo(String userInfo) {
            this.userInfo = userInfo;
        }

        public String getUserInfo() {
            return userInfo;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getHost() {
            return host;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }

        public void setFragment(String fragment) {
            this.fragment = fragment;
        }

        public String getFragment() {
            return fragment;
        }

        public String toUriString(UriFormat format) {
            return null;
        }
    }

    public static class DummyConfiguration extends DefaultEndpointConfiguration {

        private String path;
        @URIField(component = "query", parameter = "first")
        private String first;
        @URIField(component = "query", parameter = "second")
        private int second;
        
        DummyConfiguration(CamelContext camelContext) {
            super(camelContext);
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFirst() {
            return first;
        }
        
        public void setFirst(String first) {
            this.first = first;
        }

        public int getSecond() {
            return second;
        }
        
        public void setSecond(int second) {
            this.second = second;
        }

        public String toUriString(UriFormat format) {
            return null;
        }
    }
}
