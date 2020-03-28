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

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.catalog.impl.CatalogHelper.loadText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CamelCatalogTest {

    static CamelCatalog catalog;

    private static final Logger LOG = LoggerFactory.getLogger(CamelCatalogTest.class);

    @BeforeClass
    public static void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    public void testGetVersion() throws Exception {
        String version = catalog.getCatalogVersion();
        assertNotNull(version);

        String loaded = catalog.getLoadedVersion();
        assertNotNull(loaded);
        assertEquals(version, loaded);
    }

    @Test
    public void testLoadVersion() throws Exception {
        boolean result = catalog.loadVersion("1.0");
        assertFalse(result);

        String version = catalog.getCatalogVersion();
        result = catalog.loadVersion(version);
        assertTrue(result);
    }

    @Test
    public void testFindComponentNames() throws Exception {
        List<String> names = catalog.findComponentNames();
        assertNotNull(names);
        assertTrue(names.contains("file"));
        assertTrue(names.contains("log"));
        assertTrue(names.contains("docker"));
        assertTrue(names.contains("jms"));
        assertTrue(names.contains("activemq"));
        assertTrue(names.contains("zookeeper-master"));
    }

    @Test
    public void testFindOtherNames() throws Exception {
        List<String> names = catalog.findOtherNames();

        assertTrue(names.contains("hystrix"));
        assertTrue(names.contains("leveldb"));
        assertTrue(names.contains("swagger-java"));
        assertTrue(names.contains("test-spring"));

        assertFalse(names.contains("http-common"));
        assertFalse(names.contains("kura"));
        assertFalse(names.contains("core-osgi"));
        assertFalse(names.contains("file"));
        assertFalse(names.contains("ftp"));
        assertFalse(names.contains("jetty"));
    }

    @Test
    public void testFindDataFormatNames() throws Exception {
        List<String> names = catalog.findDataFormatNames();
        assertNotNull(names);
        assertTrue(names.contains("bindy-csv"));
        assertTrue(names.contains("hl7"));
        assertTrue(names.contains("jaxb"));
        assertTrue(names.contains("syslog"));
        assertTrue(names.contains("asn1"));
        assertTrue(names.contains("zipfile"));
    }

    @Test
    public void testFindLanguageNames() throws Exception {
        List<String> names = catalog.findLanguageNames();

        assertTrue(names.contains("simple"));
        assertTrue(names.contains("groovy"));
        assertTrue(names.contains("mvel"));
        assertTrue(names.contains("bean"));
        assertTrue(names.contains("file"));
        assertTrue(names.contains("xtokenize"));
        assertTrue(names.contains("hl7terser"));
    }

    @Test
    public void testFindModelNames() throws Exception {
        List<String> names = catalog.findModelNames();
        assertNotNull(names);
        assertTrue(names.contains("from"));
        assertTrue(names.contains("to"));
        assertTrue(names.contains("recipientList"));
        assertTrue(names.contains("aggregate"));
        assertTrue(names.contains("split"));
        assertTrue(names.contains("loadBalance"));
        assertTrue(names.contains("circuitBreaker"));
        assertTrue(names.contains("saga"));
    }

    @Test
    public void testJsonSchema() throws Exception {
        String schema = catalog.componentJSonSchema("docker");
        assertNotNull(schema);

        schema = catalog.dataFormatJSonSchema("hl7");
        assertNotNull(schema);

        schema = catalog.languageJSonSchema("groovy");
        assertNotNull(schema);

        schema = catalog.modelJSonSchema("aggregate");
        assertNotNull(schema);

        schema = catalog.otherJSonSchema("swagger-java");
        assertNotNull(schema);

        // lets make it possible to find bean/method using both names
        schema = catalog.modelJSonSchema("method");
        assertNotNull(schema);
        schema = catalog.modelJSonSchema("bean");
        assertNotNull(schema);
    }

    @Test
    public void testXmlSchema() throws Exception {
        String schema = catalog.springSchemaAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testArchetypeCatalog() throws Exception {
        String schema = catalog.archetypeCatalogAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testMain() throws Exception {
        String schema = catalog.mainJsonSchema();
        assertNotNull(schema);
    }

    @Test
    public void testAsEndpointUriMapFile() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("directoryName", "src/data/inbox");
        map.put("noop", "true");
        map.put("delay", "5000");

        String uri = catalog.asEndpointUri("file", map, true);
        assertEquals("file:src/data/inbox?delay=5000&noop=true", uri);

        String uri2 = catalog.asEndpointUriXml("file", map, true);
        assertEquals("file:src/data/inbox?delay=5000&amp;noop=true", uri2);
    }

    @Test
    public void testAsEndpointUriMapFtp() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("host", "someserver");
        map.put("port", "21");
        map.put("directoryName", "foo");
        map.put("connectTimeout", "5000");

        String uri = catalog.asEndpointUri("ftp", map, true);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri);

        String uri2 = catalog.asEndpointUriXml("ftp", map, true);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri2);
    }

    @Test
    public void testAsEndpointUriMapJms() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationType", "queue");
        map.put("destinationName", "foo");

        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:queue:foo", uri);
    }

    @Test
    public void testAsEndpointUriNettyhttp() throws Exception {
        Map<String, String> map = new HashMap<>();
        // use http protocol
        map.put("protocol", "http");
        map.put("host", "localhost");
        map.put("port", "8080");
        map.put("path", "foo/bar");
        map.put("disconnect", "true");

        String uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:http:localhost:8080/foo/bar?disconnect=true", uri);

        // lets switch protocol
        map.put("protocol", "https");

        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:https:localhost:8080/foo/bar?disconnect=true", uri);

        // lets set a query parameter in the path
        map.put("path", "foo/bar?verbose=true");
        map.put("disconnect", "true");

        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:https:localhost:8080/foo/bar?verbose=true&disconnect=true", uri);
    }

    @Test
    public void testAsEndpointUriTimer() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "5000");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=5000", uri);
    }

    @Test
    public void testAsEndpointDefaultValue() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationName", "cheese");
        map.put("maxMessagesPerTask", "-1");

        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:cheese?maxMessagesPerTask=-1", uri);
    }

    @Test
    public void testAsEndpointUriPropertiesPlaceholders() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "{{howoften}}");
        map.put("repeatCount", "5");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=%7B%7Bhowoften%7D%7D&repeatCount=5", uri);

        uri = catalog.asEndpointUri("timer", map, false);
        assertEquals("timer:foo?period={{howoften}}&repeatCount=5", uri);
    }

    @Test
    public void testAsEndpointUriBeanLookup() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("resourceUri", "foo.xslt");
        map.put("converter", "#myConverter");

        String uri = catalog.asEndpointUri("xslt", map, true);
        assertEquals("xslt:foo.xslt?converter=%23myConverter", uri);

        uri = catalog.asEndpointUri("xslt", map, false);
        assertEquals("xslt:foo.xslt?converter=#myConverter", uri);
    }

    @Test
    public void testAsEndpointUriMapJmsRequiredOnly() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationName", "foo");
        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:foo", uri);

        map.put("deliveryPersistent", "false");
        map.put("allowNullBody", "true");

        uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:foo?allowNullBody=true&deliveryPersistent=false", uri);

        String uri2 = catalog.asEndpointUriXml("jms", map, true);
        assertEquals("jms:foo?allowNullBody=true&amp;deliveryPersistent=false", uri2);
    }

    @Test
    public void testAsEndpointUriRestUriTemplate() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("method", "get");
        map.put("path", "api");
        map.put("uriTemplate", "user/{id}");
        String uri = catalog.asEndpointUri("rest", map, true);

        assertEquals("rest:get:api:user/{id}", uri);
    }

    @Test
    public void testAsEndpointUriNettyHttpHostnameWithDash() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.hostname.tld");
        map.put("port", "8080");
        map.put("path", "anything");
        String uri = catalog.asEndpointUri("netty-http", map, false);
        assertEquals("netty-http:http:a-b-c.hostname.tld:8080/anything", uri);

        map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.server.net");
        map.put("port", "8888");
        map.put("path", "service/v3");
        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:http:a-b-c.server.net:8888/service/v3", uri);
    }

    @Test
    public void testNettyHttpDynamicToIssueHost() throws Exception {
        String uri = "netty-http:http://a-b-c.hostname.tld:8080/anything";
        Map<String, String> params = catalog.endpointProperties(uri);
        assertEquals("http", params.get("protocol"));
        assertEquals("a-b-c.hostname.tld", params.get("host"));
        assertEquals("8080", params.get("port"));
        assertEquals("anything", params.get("path"));

        // remove path
        params.remove("path");

        String resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http:a-b-c.hostname.tld:8080", resolved);
    }

    @Test
    public void testEndpointProperties() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ftp:someserver:21/foo?connectTimeout=5000");
        assertNotNull(map);
        assertEquals(4, map.size());

        assertEquals("someserver", map.get("host"));
        assertEquals("21", map.get("port"));
        assertEquals("foo", map.get("directoryName"));
        assertEquals("5000", map.get("connectTimeout"));
    }

    @Test
    public void testEndpointLenientProperties() throws Exception {
        Map<String, String> map = catalog.endpointLenientProperties("http:myserver?throwExceptionOnFailure=false&foo=123&bar=456");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));

        map = catalog.endpointLenientProperties("http:myserver?throwExceptionOnFailure=false&foo=123&bar=456&httpClient.timeout=5000&httpClient.soTimeout=10000");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));

        map = catalog.endpointLenientProperties("http:myserver?throwExceptionOnFailure=false&foo=123&bar=456&httpClient.timeout=5000&httpClient.soTimeout=10000&myPrefix.baz=beer");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));
        assertEquals("beer", map.get("myPrefix.baz"));
    }

    @Test
    public void testEndpointPropertiesPlaceholders() throws Exception {
        Map<String, String> map = catalog.endpointProperties("timer:foo?period={{howoften}}&repeatCount=5");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("timerName"));
        assertEquals("{{howoften}}", map.get("period"));
        assertEquals("5", map.get("repeatCount"));
    }

    @Test
    public void testEndpointPropertiesNettyHttp() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty-http:http:localhost:8080/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("8080", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpDefaultPort() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty-http:http:localhost/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(5, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpPlaceholder() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty-http:http:{{myhost}}:{{myport}}/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("{{myhost}}", map.get("host"));
        assertEquals("{{myport}}", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpWithDoubleSlash() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty-http:http://localhost:8080/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("8080", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testAsEndpointUriLog() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "WARN");
        map.put("multiline", "true");
        map.put("showAll", "true");
        map.put("showBody", "false");
        map.put("showBodyType", "false");
        map.put("showExchangePattern", "false");
        map.put("style", "Tab");

        assertEquals("log:foo?loggerLevel=WARN&multiline=true&showAll=true&style=Tab", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriLogShort() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "DEBUG");

        assertEquals("log:foo?loggerLevel=DEBUG", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriWithplaceholder() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("query", "{{insert}}");
        assertEquals("sql:{{insert}}", catalog.asEndpointUri("sql", map, false));

        map.put("useMessageBodyForSql", "true");
        assertEquals("sql:{{insert}}?useMessageBodyForSql=true", catalog.asEndpointUri("sql", map, false));

        map.put("parametersCount", "{{count}}");
        assertEquals("sql:{{insert}}?parametersCount={{count}}&useMessageBodyForSql=true", catalog.asEndpointUri("sql", map, false));
    }

    @Test
    public void testAsEndpointUriStream() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("kind", "url");
        map.put("url", "http://camel.apache.org");

        assertEquals("stream:url?url=http://camel.apache.org", catalog.asEndpointUri("stream", map, false));
    }

    @Test
    public void testEndpointPropertiesJms() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:queue:foo");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("queue", map.get("destinationType"));
        assertEquals("foo", map.get("destinationName"));

        map = catalog.endpointProperties("jms:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("destinationName"));
    }

    @Test
    public void testEndpointPropertiesJmsWithDotInName() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:browse.me");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("browse.me", map.get("destinationName"));

        map = catalog.endpointProperties("jms:browse.me");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("browse.me", map.get("destinationName"));
    }

    @Test
    public void testEndpointPropertiesJmsRequired() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("destinationName"));

        map = catalog.endpointProperties("jms:foo?allowNullBody=true&deliveryPersistent=false");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("destinationName"));
        assertEquals("true", map.get("allowNullBody"));
        assertEquals("false", map.get("deliveryPersistent"));
    }

    @Test
    public void testEndpointPropertiesAtom() throws Exception {
        Map<String, String> map = catalog.endpointProperties("atom:file:src/test/data/feed.atom");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));

        map = catalog.endpointProperties("atom:file:src/test/data/feed.atom?splitEntries=false&delay=5000");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));
        assertEquals("false", map.get("splitEntries"));
        assertEquals("5000", map.get("delay"));
    }

    @Test
    public void testEndpointPropertiesMultiValued() throws Exception {
        Map<String, String> map = catalog.endpointProperties("http:helloworld?httpClientOptions=httpClient.foo=123&httpClient.bar=456");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("helloworld", map.get("httpUri"));
        assertEquals("httpClient.foo=123&httpClient.bar=456", map.get("httpClientOptions"));
    }

    @Test
    public void testEndpointPropertiesSshWithUserInfo() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ssh:localhost:8101?username=scott&password=tiger");
        assertNotNull(map);
        assertEquals(4, map.size());
        assertEquals("8101", map.get("port"));
        assertEquals("localhost", map.get("host"));
        assertEquals("scott", map.get("username"));
        assertEquals("tiger", map.get("password"));

        map = catalog.endpointProperties("ssh://scott:tiger@localhost:8101");
        assertNotNull(map);
        assertEquals(4, map.size());
        assertEquals("8101", map.get("port"));
        assertEquals("localhost", map.get("host"));
        assertEquals("scott", map.get("username"));
        assertEquals("tiger", map.get("password"));
    }

    @Test
    public void validateActiveMQProperties() throws Exception {
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.camel.component.activemq.ActiveMQComponent");

        // activemq
        EndpointValidationResult result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes", false, true, false);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes", false, false, true);
        assertTrue(result.isSuccess());

        // connection factory
        result = catalog.validateEndpointProperties("activemq:Consumer.Baz.VirtualTopic.FooRequest?connectionFactory=#pooledJmsConnectionFactory");
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateJmsProperties() throws Exception {
        // jms
        EndpointValidationResult result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes", false, true, false);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes", false, false, true);
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateProperties() throws Exception {
        // valid
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog");
        assertTrue(result.isSuccess());

        // unknown
        result = catalog.validateEndpointProperties("log:mylog?level=WARN&foo=bar");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("foo"));
        assertEquals(1, result.getNumberOfErrors());

        // enum
        result = catalog.validateEndpointProperties("jms:unknown:myqueue");
        assertFalse(result.isSuccess());
        assertEquals("unknown", result.getInvalidEnum().get("destinationType"));
        assertEquals("queue", result.getDefaultValues().get("destinationType"));
        assertEquals(1, result.getNumberOfErrors());

        // reference okay
        result = catalog.validateEndpointProperties("jms:queue:myqueue?jmsKeyFormatStrategy=#key");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getNumberOfErrors());

        // reference
        result = catalog.validateEndpointProperties("jms:queue:myqueue?jmsKeyFormatStrategy=foo");
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getInvalidEnum().get("jmsKeyFormatStrategy"));
        assertEquals(1, result.getNumberOfErrors());

        // okay
        result = catalog.validateEndpointProperties("yammer:MESSAGES?accessToken=aaa&consumerKey=bbb&consumerSecret=ccc&useJson=true&initialDelay=500");
        assertTrue(result.isSuccess());

        // required / boolean / integer
        result = catalog.validateEndpointProperties("yammer:MESSAGES?accessToken=aaa&consumerKey=&useJson=no&initialDelay=five");
        assertFalse(result.isSuccess());
        assertEquals(4, result.getNumberOfErrors());
        assertTrue(result.getRequired().contains("consumerKey"));
        assertTrue(result.getRequired().contains("consumerSecret"));
        assertEquals("no", result.getInvalidBoolean().get("useJson"));
        assertEquals("five", result.getInvalidInteger().get("initialDelay"));

        // unknown component
        result = catalog.validateEndpointProperties("foo:bar?me=you");
        assertTrue(result.isSuccess());
        assertTrue(result.hasWarnings());
        assertTrue(result.getUnknownComponent().equals("foo"));
        assertEquals(0, result.getNumberOfErrors());
        assertEquals(1, result.getNumberOfWarnings());

        // invalid boolean but default value
        result = catalog.validateEndpointProperties("log:output?showAll=ggg");
        assertFalse(result.isSuccess());
        assertEquals("ggg", result.getInvalidBoolean().get("showAll"));
        assertEquals(1, result.getNumberOfErrors());

        // dataset
        result = catalog.validateEndpointProperties("dataset:foo?minRate=50");
        assertTrue(result.isSuccess());

        // time pattern
        result = catalog.validateEndpointProperties("timer://foo?fixedRate=true&delay=0&period=2000");
        assertTrue(result.isSuccess());

        // reference lookup
        result = catalog.validateEndpointProperties("timer://foo?fixedRate=#fixed&delay=#myDelay");
        assertTrue(result.isSuccess());

        // optional consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?consumer.delay=5000&consumer.greedy=true");
        assertTrue(result.isSuccess());

        // optional without consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&greedy=true");
        assertTrue(result.isSuccess());

        // mixed optional without consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&consumer.greedy=true");
        assertTrue(result.isSuccess());

        // prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&scheduler.foo=123&scheduler.bar=456");
        assertTrue(result.isSuccess());

        // stub
        result = catalog.validateEndpointProperties("stub:foo?me=123&you=456");
        assertTrue(result.isSuccess());

        // lenient on
        result = catalog.validateEndpointProperties("dataformat:string:marshal?foo=bar");
        assertTrue(result.isSuccess());

        // lenient off
        result = catalog.validateEndpointProperties("dataformat:string:marshal?foo=bar", true);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("foo"));

        // lenient off consumer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", false, true, false);
        assertFalse(result.isSuccess());
        // consumer should still fail because we cannot use lenient option in consumer mode
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());
        // lenient off producer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", false, false, true);
        assertTrue(result.isSuccess());
        // foo is the lenient option
        assertEquals(1, result.getLenient().size());
        assertEquals("foo", result.getLenient().iterator().next());

        // lenient on consumer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", true, true, false);
        assertFalse(result.isSuccess());
        // consumer should still fail because we cannot use lenient option in consumer mode
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());
        // lenient on producer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", true, false, true);
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());

        // lenient on rss consumer only
        result = catalog.validateEndpointProperties("rss:file:src/test/data/rss20.xml?splitEntries=true&sortEntries=true&consumer.delay=50&foo=bar", false, true, false);
        assertTrue(result.isSuccess());
        assertEquals("foo", result.getLenient().iterator().next());

        // data format
        result = catalog.validateEndpointProperties("dataformat:zipdeflater:marshal?compressionLevel=2", true);
        assertTrue(result.isSuccess());

        // 2 slash after component name
        result = catalog.validateEndpointProperties("atmos://put?remotePath=/dummy.txt");
        assertTrue(result.isSuccess());

        // userinfo in authority with username and password
        result = catalog.validateEndpointProperties("ssh://karaf:karaf@localhost:8101");
        assertTrue(result.isSuccess());

        // userinfo in authority without password
        result = catalog.validateEndpointProperties("ssh://scott@localhost:8101?certResource=classpath:test_rsa&useFixedDelay=true&delay=5000&pollCommand=features:list%0A");
        assertTrue(result.isSuccess());

        // userinfo with both user and password and placeholder
        result = catalog.validateEndpointProperties("ssh://smx:smx@localhost:8181?timeout=3000");
        assertTrue(result.isSuccess());
        // and should also work when port is using a placeholder
        result = catalog.validateEndpointProperties("ssh://smx:smx@localhost:{{port}}?timeout=3000");
        assertTrue(result.isSuccess());

        // placeholder for a bunch of optional options
        result = catalog.validateEndpointProperties("aws-swf://activity?{{options}}");
        assertTrue(result.isSuccess());

        // incapable to parse
        result = catalog.validateEndpointProperties("{{getFtpUrl}}?recursive=true");
        assertTrue(result.isSuccess());
        assertTrue(result.hasWarnings());
        assertTrue(result.getIncapable() != null);
    }

    @Test
    public void validatePropertiesSummary() throws Exception {
        EndpointValidationResult result = catalog.validateEndpointProperties("yammer:MESSAGES?blah=yada&accessToken=aaa&consumerKey=&useJson=no&initialDelay=five&pollStrategy=myStrategy");
        assertFalse(result.isSuccess());
        String reason = result.summaryErrorMessage(true);
        LOG.info(reason);

        result = catalog.validateEndpointProperties("jms:unknown:myqueue");
        assertFalse(result.isSuccess());
        reason = result.summaryErrorMessage(false);
        LOG.info(reason);
    }

    @Test
    public void validateTimePattern() throws Exception {
        assertTrue(catalog.validateTimePattern("0"));
        assertTrue(catalog.validateTimePattern("500"));
        assertTrue(catalog.validateTimePattern("10000"));
        assertTrue(catalog.validateTimePattern("5s"));
        assertTrue(catalog.validateTimePattern("5sec"));
        assertTrue(catalog.validateTimePattern("5secs"));
        assertTrue(catalog.validateTimePattern("3m"));
        assertTrue(catalog.validateTimePattern("3min"));
        assertTrue(catalog.validateTimePattern("3minutes"));
        assertTrue(catalog.validateTimePattern("5m15s"));
        assertTrue(catalog.validateTimePattern("1h"));
        assertTrue(catalog.validateTimePattern("1hour"));
        assertTrue(catalog.validateTimePattern("2hours"));

        assertFalse(catalog.validateTimePattern("bla"));
        assertFalse(catalog.validateTimePattern("2year"));
        assertFalse(catalog.validateTimePattern("60darn"));
    }

    @Test
    public void testEndpointComponentName() throws Exception {
        String name = catalog.endpointComponentName("jms:queue:foo");
        assertEquals("jms", name);
    }

    @Test
    public void testListComponentsAsJson() throws Exception {
        String json = catalog.listComponentsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListDataFormatsAsJson() throws Exception {
        String json = catalog.listDataFormatsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListLanguagesAsJson() throws Exception {
        String json = catalog.listLanguagesAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListModelsAsJson() throws Exception {
        String json = catalog.listModelsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListOthersAsJson() throws Exception {
        String json = catalog.listOthersAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testSummaryAsJson() throws Exception {
        String json = catalog.summaryAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponent() throws Exception {
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent");

        assertTrue(catalog.findComponentNames().contains("dummy"));

        String json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponentWithJson() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummy.json"));
        assertNotNull(json);
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent", json);

        assertTrue(catalog.findComponentNames().contains("dummy"));

        json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponentWithPrettyJson() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummy-pretty.json"));
        assertNotNull(json);
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent", json);

        assertTrue(catalog.findComponentNames().contains("dummy"));

        json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormat() throws Exception {
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat");

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        String json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormatWithJSon() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummyformat.json"));
        assertNotNull(json);
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat", json);

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormatWithPrettyJSon() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummyformat-pretty.json"));
        assertNotNull(json);
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat", json);

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testSimpleExpression() throws Exception {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", "${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getText());

        result = catalog.validateLanguageExpression(null, "simple", "${body");
        assertFalse(result.isSuccess());
        assertEquals("${body", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 5"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(5, result.getIndex());

        result = catalog.validateLanguageExpression(null, "simple", "${bodyxxx}");
        assertFalse(result.isSuccess());
        assertEquals("${bodyxxx}", result.getText());
        LOG.info(result.getError());
        assertEquals("Valid syntax: ${body.OGNL} was: bodyxxx", result.getShortError());
        assertEquals(0, result.getIndex());
    }

    @Test
    public void testSimplePredicate() throws Exception {
        LanguageValidationResult result = catalog.validateLanguagePredicate(null, "simple", "${body} == 'abc'");
        assertTrue(result.isSuccess());
        assertEquals("${body} == 'abc'", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${body} > ${header.size");
        assertFalse(result.isSuccess());
        assertEquals("${body} > ${header.size", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 22"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(22, result.getIndex());
    }

    @Test
    public void testPredicatePlaceholder() throws Exception {
        LanguageValidationResult result = catalog.validateLanguagePredicate(null, "simple", "${body} contains '{{danger}}'");
        assertTrue(result.isSuccess());
        assertEquals("${body} contains '{{danger}}'", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${bdy} contains '{{danger}}'");
        assertFalse(result.isSuccess());
        assertEquals("${bdy} contains '{{danger}}'", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("Unknown function: bdy at location 0"));
        assertTrue(result.getError().contains("'{{danger}}'"));
        assertEquals("Unknown function: bdy", result.getShortError());
        assertEquals(0, result.getIndex());
    }

    @Test
    public void testValidateLanguage() throws Exception {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", "${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getText());

        result = catalog.validateLanguageExpression(null, "header", "foo");
        assertTrue(result.isSuccess());
        assertEquals("foo", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${body} > 10");
        assertTrue(result.isSuccess());
        assertEquals("${body} > 10", result.getText());

        result = catalog.validateLanguagePredicate(null, "header", "bar");
        assertTrue(result.isSuccess());
        assertEquals("bar", result.getText());

        result = catalog.validateLanguagePredicate(null, "foobar", "bar");
        assertFalse(result.isSuccess());
        assertEquals("Unknown language foobar", result.getError());
    }

    @Test
    public void testValidateJSonPathLanguage() throws Exception {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "jsonpath", "$.store.book[?(@.price < 10)]");
        assertTrue(result.isSuccess());
        assertEquals("$.store.book[?(@.price < 10)]", result.getText());

        result = catalog.validateLanguageExpression(null, "jsonpath", "$.store.book[?(@.price ^^^ 10)]");
        assertFalse(result.isSuccess());
        assertEquals("$.store.book[?(@.price ^^^ 10)]", result.getText());
        assertEquals("Illegal syntax: $.store.book[?(@.price ^^^ 10)]", result.getError());
    }

    @Test
    public void testSpringCamelContext() throws Exception {
        String json = catalog.modelJSonSchema("camelContext");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);

        assertTrue(json.contains("CamelContext using XML configuration"));
    }

    @Test
    public void testComponentAsciiDoc() throws Exception {
        String doc = catalog.componentAsciiDoc("mock");
        assertNotNull(doc);
        assertTrue(doc.contains("mock:someName"));

        doc = catalog.componentAsciiDoc("geocoder");
        assertNotNull(doc);
        assertTrue(doc.contains("looking up geocodes"));

        doc = catalog.componentAsciiDoc("smtp");
        assertNotNull(doc);
        assertTrue(doc.contains("The mail component"));

        doc = catalog.componentAsciiDoc("unknown");
        assertNull(doc);
    }

    @Test
    public void testTransactedAndPolicyNoOutputs() throws Exception {
        String json = catalog.modelJSonSchema("transacted");
        assertNotNull(json);
        assertTrue(json.contains("\"output\": false"));
        assertFalse(json.contains("\"outputs\":"));

        json = catalog.modelJSonSchema("policy");
        assertNotNull(json);
        assertTrue(json.contains("\"output\": false"));
        assertFalse(json.contains("\"outputs\":"));
    }

    @Test
    public void testDataFormatAsciiDoc() throws Exception {
        String doc = catalog.dataFormatAsciiDoc("json-jackson");
        assertNotNull(doc);
        assertTrue(doc.contains("Jackson dataformat"));

        doc = catalog.dataFormatAsciiDoc("bindy-csv");
        assertNotNull(doc);
        assertTrue(doc.contains("CsvRecord"));
    }

    @Test
    public void testLanguageAsciiDoc() throws Exception {
        String doc = catalog.languageAsciiDoc("jsonpath");
        assertNotNull(doc);
        assertTrue(doc.contains("JSonPath language"));
    }

    @Test
    public void testOtherAsciiDoc() throws Exception {
        String doc = catalog.otherAsciiDoc("swagger-java");
        assertNotNull(doc);
        assertTrue(doc.contains("Swagger"));
    }

    @Test
    public void testValidateEndpointTwitterSpecial() throws Exception {
        String uri = "twitter-search://java?{{%s}}";

        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testValidateEndpointHttpPropertyPlaceholder() throws Exception {
        String uri = "http://api.openweathermap.org/data/2.5/weather?{{property.weatherUri}}";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties(uri, true);
        assertTrue(result.isSuccess());

        // use incorrect style using ${ } as placeholder
        uri = "http://api.openweathermap.org/data/2.5/weather?${property.weatherUri}";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties(uri, true);
        assertFalse(result.isSuccess());
        assertEquals("${property.weatherUri}", result.getUnknown().iterator().next());
    }

    @Test
    public void testValidateEndpointJmsDefault() throws Exception {
        String uri = "jms:cheese?maxMessagesPerTask=-1";

        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getDefaultValues().size());
        assertEquals("-1", result.getDefaultValues().get("maxMessagesPerTask"));
    }

    @Test
    public void testValidateEndpointConsumerOnly() throws Exception {
        String uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, true, false);
        assertTrue(result.isSuccess());

        uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true&fileExist=Append";
        result = catalog.validateEndpointProperties(uri, false, true, false);
        assertFalse(result.isSuccess());

        assertEquals("fileExist", result.getNotConsumerOnly().iterator().next());
    }

    @Test
    public void testValidateEndpointProducerOnly() throws Exception {
        String uri = "file:outbox?bufferSize=4096&fileExist=Append";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, false, true);
        assertTrue(result.isSuccess());

        uri = "file:outbox?bufferSize=4096&fileExist=Append&delete=true";
        result = catalog.validateEndpointProperties(uri, false, false, true);
        assertFalse(result.isSuccess());

        assertEquals("delete", result.getNotProducerOnly().iterator().next());
    }

    @Test
    public void testNettyHttpDynamicToIssue() throws Exception {
        String uri = "netty-http:http://10.192.1.10:8080/client/alerts/summary?throwExceptionOnFailure=false";
        Map<String, String> params = catalog.endpointProperties(uri);
        params.remove("path");
        params.remove("throwExceptionOnFailure");

        String resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http:10.192.1.10:8080", resolved);

        // another example with dash in hostname
        uri = "netty-http:http://a-b-c.hostname.tld:8080/anything";
        params = catalog.endpointProperties(uri);
        resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http:a-b-c.hostname.tld:8080/anything", resolved);
    }

    @Test
    public void testValidateConfigurationPropertyComponent() throws Exception {
        String text = "camel.component.seda.queueSize=1234";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queue-size=1234";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queuesize=1234";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queueSize=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidInteger().get("camel.component.seda.queueSize"));

        text = "camel.component.seda.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.component.seda.foo"));

        text = "camel.component.jms.acknowledgementModeName=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidEnum().get("camel.component.jms.acknowledgementModeName"));
        List<String> list = result.getEnumChoices("camel.component.jms.acknowledgementModeName");
        assertEquals(4, list.size());
        assertEquals("SESSION_TRANSACTED", list.get(0));
        assertEquals("CLIENT_ACKNOWLEDGE", list.get(1));
        assertEquals("AUTO_ACKNOWLEDGE", list.get(2));
        assertEquals("DUPS_OK_ACKNOWLEDGE", list.get(3));
    }

    @Test
    public void testValidateConfigurationPropertyLanguage() throws Exception {
        String text = "camel.language.tokenize.token=;";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.language.tokenize.regex=true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.language.tokenize.regex=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.language.tokenize.regex"));

        text = "camel.language.tokenize.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.language.tokenize.foo"));
    }

    @Test
    public void testValidateConfigurationPropertyDataformat() throws Exception {
        String text = "camel.dataformat.bindy-csv.type=csv";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.dataformat.bindy-csv.locale=us";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.dataformat.bindy-csv.allowEmptyStream=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.dataformat.bindy-csv.allowEmptyStream"));

        text = "camel.dataformat.bindy-csv.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.dataformat.bindy-csv.foo"));

        text = "camel.dataformat.bindy-csv.type=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidEnum().get("camel.dataformat.bindy-csv.type"));
        List<String> list = result.getEnumChoices("camel.dataformat.bindy-csv.type");
        assertEquals(3, list.size());
        assertEquals("Csv", list.get(0));
        assertEquals("Fixed", list.get(1));
        assertEquals("KeyValue", list.get(2));
    }

    @Test
    public void testValidateConfigurationPropertyComponentQuartz() throws Exception {
        String text = "camel.component.quartz.auto-start-scheduler=true";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties=#myProp";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties=123";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());

        text = "camel.component.quartz.properties.foo=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties.bar=true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[0]=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[1]=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo]=abc";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo].beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo].drink=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testValidateConfigurationPropertyComponentJClouds() throws Exception {
        String text = "camel.component.jclouds.basicPropertyBinding=true";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores=#myStores";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores=foo";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getInvalidArray().containsKey("camel.component.jclouds.blobStores"));

        text = "camel.component.jclouds.blobStores[0]=foo";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores[1]=bar";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores[foo]=123";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getInvalidInteger().get("camel.component.jclouds.blobStores[foo]"));

        text = "camel.component.jclouds.blobStores[0].beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores[1].drink=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.jclouds.blobStores[foo].beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getInvalidInteger().get("camel.component.jclouds.blobStores[foo].beer"));
    }

    @Test
    public void testValidateConfigurationPropertyMain() throws Exception {
        String text = "camel.main.allow-use-original-message=true";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.main.allow-use-original-message=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.main.allow-use-original-message"));

        text = "camel.main.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.main.foo"));

        text = "camel.resilience4j.minimum-number-of-calls=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.resilience4j.minimum-number-of-calls=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidInteger().get("camel.resilience4j.minimum-number-of-calls"));

        text = "camel.resilience4j.slow-call-rate-threshold=12.5";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.resilience4j.slow-call-rate-threshold=12x5";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("12x5", result.getInvalidNumber().get("camel.resilience4j.slow-call-rate-threshold"));
    }

    @Test
    public void testValidateConfigurationPropertyMainMap() throws Exception {
        String text = "camel.rest.api-properties=#foo";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties=bar";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("bar", result.getInvalidMap().get("camel.rest.api-properties"));

        text = "camel.rest.api-properties.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties.bar=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties.beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties[drink]=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
    }

}
