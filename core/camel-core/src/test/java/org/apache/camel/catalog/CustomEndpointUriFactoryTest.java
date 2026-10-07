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
package org.apache.camel.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomEndpointUriFactoryTest extends ContextTestSupport {

    @Test
    public void testCustomAssemble() throws Exception {
        EndpointUriFactory assembler = new MyAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "foo");
        params.put("amount", "123");
        params.put("port", 4444);
        params.put("verbose", true);

        String uri = assembler.buildUri("acme", params);
        Assertions.assertEquals("acme:foo:4444?amount=123&verbose=true", uri);
    }

    @Test
    public void testCustomUriFactoryRegistry() throws Exception {
        EndpointUriFactory assembler = new MyAssembler();
        context.getRegistry().bind("myAssembler", assembler);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "foo");
        params.put("amount", "123");
        params.put("port", 4444);
        params.put("verbose", true);

        assembler = context.getCamelContextExtension().getEndpointUriFactory("acme");
        String uri = assembler.buildUri("acme", params);
        Assertions.assertEquals("acme:foo:4444?amount=123&verbose=true", uri);
    }

    @Test
    public void testCustomAssembleInsertionOrder() throws Exception {
        EndpointUriFactory assembler = new MyAssembler();
        assembler.setCamelContext(context);

        // an ordered map keeps its order (such as the parameters of a route in the YAML DSL)
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "foo");
        params.put("verbose", false);
        params.put("port", 4444);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme", params);
        Assertions.assertEquals("acme:foo:4444?verbose=false&amount=123", uri);
    }

    @Test
    public void testCustomAssembleUnordered() throws Exception {
        EndpointUriFactory assembler = new MyAssembler();
        assembler.setCamelContext(context);

        // a map without an order is sorted
        Map<String, Object> params = new HashMap<>();
        params.put("name", "foo");
        params.put("verbose", false);
        params.put("port", 4444);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme", params);
        Assertions.assertEquals("acme:foo:4444?amount=123&verbose=false", uri);
    }

    @Test
    public void testCustomAssembleNoMandatory() {
        EndpointUriFactory assembler = new MyAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("verbose", false);
        params.put("port", 4444);
        params.put("amount", "123");

        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> assembler.buildUri("acme", params),
                "Should have thrown an exception");
        Assertions.assertEquals("Option name is required when creating endpoint uri with syntax acme:name:port",
                e.getMessage());
    }

    @Test
    public void testCustomAssembleDefault() throws Exception {
        EndpointUriFactory assembler = new MyAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bar");
        params.put("verbose", false);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme", params);
        Assertions.assertEquals("acme:bar?verbose=false&amount=123", uri);
    }

    @Test
    public void testCustomAssembleComplex() throws Exception {
        EndpointUriFactory assembler = new MySecondAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bar");
        params.put("path", "moes");
        params.put("verbose", true);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme2", params);
        Assertions.assertEquals("acme2:bar/moes?verbose=true&amount=123", uri);
    }

    @Test
    public void testCustomAssembleComplexPort() throws Exception {
        EndpointUriFactory assembler = new MySecondAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bar");
        params.put("path", "moes");
        params.put("port", "4444");
        params.put("verbose", true);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme2", params);
        Assertions.assertEquals("acme2:bar/moes:4444?verbose=true&amount=123", uri);
    }

    @Test
    public void testCustomAssembleComplexNoPath() throws Exception {
        EndpointUriFactory assembler = new MySecondAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bar");
        params.put("port", "4444");
        params.put("verbose", true);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme2", params);
        Assertions.assertEquals("acme2:bar:4444?verbose=true&amount=123", uri);
    }

    @Test
    public void testCustomAssembleComplexNoPathNoPort() throws Exception {
        EndpointUriFactory assembler = new MySecondAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bar");
        params.put("verbose", true);
        params.put("amount", "123");

        String uri = assembler.buildUri("acme2", params);
        Assertions.assertEquals("acme2:bar?verbose=true&amount=123", uri);
    }

    @Test
    public void testValueWithTheNameOfAnUnsetOptionalPathParameter() throws Exception {
        // CAMEL-25383: removing the unset optional path and port found their names inside the value of name
        EndpointUriFactory assembler = new MySecondAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "{{path}}");
        params.put("verbose", true);
        Assertions.assertEquals("acme2:{{path}}?verbose=true", assembler.buildUri("acme2", params));

        params = new LinkedHashMap<>();
        params.put("name", "files/path-port");
        Assertions.assertEquals("acme2:files/path-port", assembler.buildUri("acme2", params));

        params = new LinkedHashMap<>();
        params.put("name", "{{shareName}}/{{path}}");
        params.put("port", "4444");
        Assertions.assertEquals("acme2:{{shareName}}/{{path}}:4444", assembler.buildUri("acme2", params));
    }

    @Test
    public void testJms() throws Exception {
        EndpointUriFactory assembler = new MyJmsAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("destinationType", "topic");
        params.put("deliveryPersistent", true);

        String uri = assembler.buildUri("jms2", params);
        Assertions.assertEquals("jms2:topic:foo?deliveryPersistent=true", uri);
    }

    @Test
    public void testJmsMatchDefault() throws Exception {
        EndpointUriFactory assembler = new MyJmsAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("destinationType", "queue");
        params.put("deliveryPersistent", true);

        String uri = assembler.buildUri("jms2", params);
        Assertions.assertEquals("jms2:queue:foo?deliveryPersistent=true", uri);
    }

    @Test
    public void testJmsNoDefault() throws Exception {
        EndpointUriFactory assembler = new MyJmsAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("deliveryPersistent", true);

        String uri = assembler.buildUri("jms2", params);
        Assertions.assertEquals("jms2:foo?deliveryPersistent=true", uri);
    }

    @Test
    public void testCQLAssembler() throws Exception {
        EndpointUriFactory assembler = new MyCQLAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("host", "localhost");
        params.put("keyspace", "test");
        params.put("cql", "insert into test_data(id, text) values (now(), ?)");

        Assertions.assertEquals(
                "cql:localhost/test?cql=insert+into+test_data%28id%2C+text%29+values+%28now%28%29%2C+%3F%29",
                assembler.buildUri("cql", new LinkedHashMap<>(params)));
        Assertions.assertEquals(
                "cql:localhost/test?cql=insert+into+test_data%28id%2C+text%29+values+%28now%28%29%2C+%3F%29",
                assembler.buildUri("cql", new LinkedHashMap<>(params), true));
        Assertions.assertEquals(
                "cql:localhost/test?cql=insert into test_data(id, text) values (now(), ?)",
                assembler.buildUri("cql", new LinkedHashMap<>(params), false));
    }

    @Test
    public void testCQLWithPlus() throws Exception {
        EndpointUriFactory assembler = new MyCQLAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("host", "localhost");
        params.put("keyspace", "test");
        params.put("cql", "add(4 + 5)");

        Assertions.assertEquals(
                "cql:localhost/test?cql=add%284+%2B+5%29",
                assembler.buildUri("cql", new LinkedHashMap<>(params)));
        Assertions.assertEquals(
                "cql:localhost/test?cql=add%284+%2B+5%29",
                assembler.buildUri("cql", new LinkedHashMap<>(params), true));
        Assertions.assertEquals(
                "cql:localhost/test?cql=add(4 + 5)",
                assembler.buildUri("cql", new LinkedHashMap<>(params), false));
    }

    @Test
    public void testJmsSecrets() throws Exception {
        EndpointUriFactory assembler = new MyJmsxAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("deliveryPersistent", true);
        params.put("username", "usr");
        params.put("password", "pwd");

        String uri = assembler.buildUri("jmsx", params);
        Assertions.assertEquals("jmsx:foo?deliveryPersistent=true&username=RAW(usr)&password=RAW(pwd)", uri);
    }

    @Test
    public void testJmsMultiValued() throws Exception {
        EndpointUriFactory assembler = new MyJmsxAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("deliveryPersistent", true);
        params.put("tags", Map.of("foo", 123, "bar", 456, "baz", "cheese"));

        String uri = assembler.buildUri("jmsx", params);
        Assertions.assertEquals("jmsx:foo?deliveryPersistent=true&tag.bar=456&tag.baz=cheese&tag.foo=123", uri);
    }

    @Test
    public void testJmsMultiValuedInsertionOrder() throws Exception {
        EndpointUriFactory assembler = new MyJmsxAssembler();
        assembler.setCamelContext(context);

        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("foo", 123);
        tags.put("bar", 456);

        // the options of the multi valued map are where the map is, in the order of the map
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("destinationName", "foo");
        params.put("tags", tags);
        params.put("deliveryPersistent", true);

        String uri = assembler.buildUri("jmsx", params);
        Assertions.assertEquals("jmsx:foo?tag.foo=123&tag.bar=456&deliveryPersistent=true", uri);
    }

    private static class MyAssembler extends EndpointUriFactorySupport implements EndpointUriFactory {

        private static final String SYNTAX = "acme:name:port";

        @Override
        public boolean isEnabled(String scheme) {
            return "acme".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            // begin from syntax
            String uri = SYNTAX;

            // append path parameters
            uri = buildPathParameter(SYNTAX, uri, "name", null, true, properties);
            uri = buildPathParameter(SYNTAX, uri, "port", 8080, false, properties);
            // append remainder parameters
            uri = buildQueryParameters(uri, properties, encode);

            return uri;
        }

        @Override
        public Set<String> propertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }

    }

    private static class MySecondAssembler extends EndpointUriFactorySupport implements EndpointUriFactory {

        private static final String SYNTAX = "acme2:name/path:port";

        @Override
        public boolean isEnabled(String scheme) {
            return "acme2".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            // begin from syntax
            String uri = SYNTAX;

            // append path parameters
            uri = buildPathParameter(SYNTAX, uri, "name", null, true, properties);
            uri = buildPathParameter(SYNTAX, uri, "path", null, false, properties);
            uri = buildPathParameter(SYNTAX, uri, "port", 8080, false, properties);
            // append remainder parameters
            uri = buildQueryParameters(uri, properties, encode);

            return uri;
        }

        @Override
        public Set<String> propertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }

    }

    private static class MyJmsAssembler extends EndpointUriFactorySupport implements EndpointUriFactory {

        private static final String SYNTAX = "jms2:destinationType:destinationName";

        @Override
        public boolean isEnabled(String scheme) {
            return "jms2".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            String uri = SYNTAX;
            uri = buildPathParameter(SYNTAX, uri, "destinationType", "queue", false, properties);
            uri = buildPathParameter(SYNTAX, uri, "destinationName", null, true, properties);
            uri = buildQueryParameters(uri, properties, encode);

            return uri;
        }

        @Override
        public Set<String> propertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }

    }

    private static class MyJmsxAssembler extends EndpointUriFactorySupport implements EndpointUriFactory {
        private static final String SYNTAX = "jmsx:destinationType:destinationName";

        @Override
        public boolean isEnabled(String scheme) {
            return "jmsx".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            String uri = SYNTAX;
            uri = buildPathParameter(SYNTAX, uri, "destinationType", "queue", false, properties);
            uri = buildPathParameter(SYNTAX, uri, "destinationName", null, true, properties);
            uri = buildQueryParameters(uri, properties, encode);

            return uri;
        }

        @Override
        public Set<String> propertyNames() {
            return new HashSet<>(Arrays.asList("destinationType", "destinationName", "username", "password"));
        }

        @Override
        public Set<String> secretPropertyNames() {
            return new HashSet<>(Arrays.asList("username", "password"));
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Map.of("tags", "tag.");
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }

    }

    private static class MyCQLAssembler extends EndpointUriFactorySupport implements EndpointUriFactory {
        private static final String SYNTAX = "cql:host/keyspace";

        @Override
        public boolean isEnabled(String scheme) {
            return "cql".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            String uri = SYNTAX;
            uri = buildPathParameter(SYNTAX, uri, "host", null, true, properties);
            uri = buildPathParameter(SYNTAX, uri, "keyspace", null, true, properties);
            uri = buildQueryParameters(uri, properties, encode);

            return uri;
        }

        @Override
        public Set<String> propertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }

    }

}
